package com.hizeric.shenaniganscore.battleground.arena.diff;

import com.hizeric.shenaniganscore.battleground.arena.MapIds;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Single async writer for {@code battleground/arena/<mapId>/diff.log}.
 *
 * <p>The main thread only calls {@link #enqueue(Set)}: a short lock plus an O(1) queue add. One
 * dedicated thread owns every filesystem operation and every piece of per-round merged state.</p>
 *
 * <p>What changed from the first version:</p>
 * <ul>
 *   <li><b>Per-round state object.</b> Each {@link #open(String, long)} creates a fresh {@code Round}
 *       with its own inbox and merged set. Late tasks from an old round can never see, drain or
 *       wipe the new round's events.</li>
 *   <li><b>Periodic folding.</b> The worker merges the inbox into a {@code Set} every second, so a
 *       long round with thousands of small batches does not hold thousands of tiny sets in memory
 *       until the final flush.</li>
 *   <li><b>Atomic commits.</b> Logs are written to a temp file, {@code fsync}ed, then moved over
 *       {@code diff.log}. A crash mid-write leaves the previous (uncommitted) header intact instead
 *       of a half-written file.</li>
 * </ul>
 *
 * <p>Round lifecycle still belongs to Task 8: it decides when to call {@link #open(String, long)}
 * and {@link #flushAndClose()}. This class never guesses a round id.</p>
 */
public final class AsyncDiffWriter {

    private static final long FOLD_PERIOD_MS = 1_000L;
    private static final String TMP_SUFFIX = ".tmp";
    /** Refuse a diff log larger than this before any byte is read into memory. */
    private static final long MAX_DIFF_BYTES = 64L * 1024L * 1024L;

    /** Everything that belongs to exactly one round. */
    private static final class Round {
        final String mapId;
        final long roundId;
        final ConcurrentLinkedQueue<Set<Long>> inbox = new ConcurrentLinkedQueue<>();
        /** Touched only by the writer thread. */
        final Set<Long> merged = new HashSet<>();

        Round(String mapId, long roundId) {
            this.mapId = mapId;
            this.roundId = roundId;
        }

        /** Writer thread only: move everything queued so far into {@link #merged}. */
        void fold() {
            Set<Long> batch;
            while ((batch = inbox.poll()) != null) merged.addAll(batch);
        }
    }

    private final Path arenaRoot;
    private final Logger logger;
    private final ScheduledThreadPoolExecutor worker;
    private final ScheduledFuture<?> foldTask;
    private final Object gateLock = new Object();

    /** Non-null exactly while a round accepts events. Guarded by {@link #gateLock}. */
    private Round current;

    /** Test-only hook invoked on the writer thread before the committed write; null in production. */
    volatile Runnable beforeCommittedWrite;

    public AsyncDiffWriter(Path arenaRoot, Logger logger) {
        this.arenaRoot = Objects.requireNonNull(arenaRoot, "arenaRoot");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.worker = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "arena-diff-writer");
            thread.setDaemon(true);
            return thread;
        });
        this.worker.setRemoveOnCancelPolicy(true);
        this.foldTask = worker.scheduleWithFixedDelay(this::foldCurrent,
                FOLD_PERIOD_MS, FOLD_PERIOD_MS, TimeUnit.MILLISECONDS);
    }

    /** Opens a round with an unspecified (0) round id. Prefer {@link #open(String, long)}. */
    public void open(String mapId) {
        open(mapId, 0L);
    }

    /** Clears the previous log and writes an uncommitted header for this round. */
    public void open(String mapId, long roundId) {
        String validated = validateMapId(mapId);
        Round round = new Round(validated, roundId);
        synchronized (gateLock) {
            // Events are accepted from this moment on; they belong to the new round's own inbox.
            current = round;
        }
        worker.execute(() -> resetLog(round));
    }

    /** True while a round is open and accepting events. */
    public boolean isOpen() {
        synchronized (gateLock) {
            return current != null;
        }
    }

    /** Main-thread enqueue: no I/O, no world access. Late batches are rejected loudly. */
    public void enqueue(Set<Long> positions) {
        if (!tryEnqueue(positions) && positions != null && !positions.isEmpty()) {
            logger.warning("Arena diff enqueue rejected: round is already closed ("
                    + positions.size() + " position(s))");
        }
    }

    /**
     * Gate check plus queue add under one lock, so a concurrent {@link #flushAndClose()} can never
     * drain past an accepted batch. Package-private for deterministic gate-race coverage.
     *
     * @return true when the batch was accepted into the current round
     */
    boolean tryEnqueue(Set<Long> positions) {
        if (positions == null || positions.isEmpty()) return false;
        synchronized (gateLock) {
            if (current == null) return false;
            current.inbox.add(Set.copyOf(positions));
            return true;
        }
    }

    /**
     * Closes the gate, then drains, writes and fsyncs the committed log. Blocks only at a round
     * boundary, never in a listener. Safe to call repeatedly; a closed writer is a no-op.
     */
    public void flushAndClose() {
        final Round round;
        synchronized (gateLock) {
            round = current;
            if (round == null) return;
            // After this point nothing can enter the inbox, so the commit task owns every
            // batch that was ever accepted.
            current = null;
        }
        Future<?> commit = worker.submit(() -> commit(round));
        try {
            commit.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while flushing arena diff log", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            throw new IllegalStateException("Arena diff flush failed", cause);
        }
    }

    /** Plugin shutdown: flush any live round, then stop the single writer thread. */
    public void shutdown() {
        try {
            if (isOpen()) flushAndClose();
        } finally {
            foldTask.cancel(false);
            worker.shutdown();
            try {
                if (!worker.awaitTermination(10L, TimeUnit.SECONDS)) worker.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                worker.shutdownNow();
            }
        }
    }

    /** Path to a map's log, exposed so the restart path can read and validate it. */
    public Path logPath(String mapId) {
        return logFile(validateMapId(mapId));
    }

    /** Convenience for the restart path: decode a log file without duplicating file reading. */
    public static DiffLog.Decoded read(Path file) throws IOException {
        long size = Files.size(file);
        if (size > MAX_DIFF_BYTES) {
            throw new IOException("arena diff log is too large (" + size + " bytes, max "
                    + MAX_DIFF_BYTES + "): " + file);
        }
        return DiffLog.decode(Files.readAllBytes(file));
    }

    // ------------------------------------------------------------------ writer thread

    private void foldCurrent() {
        try {
            Round round;
            synchronized (gateLock) {
                round = current;
            }
            if (round != null) round.fold();
        } catch (RuntimeException e) {
            // A scheduled task that throws is silently cancelled; keep folding alive.
            logger.log(Level.WARNING, "Arena diff fold failed", e);
        }
    }

    private void resetLog(Round round) {
        try {
            // An incomplete header is the commit flag: without a later flush this round is
            // untrusted, even if some entries had already been queued.
            writeAtomic(logFile(round.mapId),
                    DiffLog.encode(round.mapId, round.roundId, Set.of(), false));
        } catch (IOException | RuntimeException e) {
            logger.log(Level.WARNING, "Could not initialise arena diff log for " + round.mapId, e);
        }
    }

    private void commit(Round round) {
        round.fold();

        Runnable hook = beforeCommittedWrite;
        if (hook != null) hook.run();

        byte[] bytes = DiffLog.encode(round.mapId, round.roundId, round.merged, true);
        try {
            writeAtomic(logFile(round.mapId), bytes);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Could not flush arena diff log for " + round.mapId, e);
            throw new IllegalStateException("Arena diff flush failed for " + round.mapId, e);
        }
    }

    /** Temp file, fsync, then replace the target in a single move. */
    private static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
        try {
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path logFile(String id) {
        return arenaRoot.resolve(id).resolve("diff.log");
    }

    private static String validateMapId(String mapId) {
        String normalized = MapIds.normalize(mapId);
        if (!MapIds.isValid(normalized)) {
            throw new IllegalArgumentException("Invalid arena map id: " + mapId);
        }
        return normalized;
    }
}
