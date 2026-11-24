package com.hizeric.shenaniganscore.core.terrain;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Durable handoff for domain-floor blocks that could not be restored because their chunk was
 * unloaded when the domain ended (issue #35).
 *
 * <p>{@link DomainFloor#restoreNow()} runs at shutdown, where there is no next tick to wait for an
 * async chunk load. It used to ignore the failed columns and then destroy the plan, stranding the
 * floor blocks in the world forever. The pending blocks are now written to a small binary ledger
 * before the plan is cleared, and replayed at the next enable with the same async-chunk-load
 * discipline the normal restore path uses.</p>
 *
 * <p>All file operations are atomic (temp file + move) so a crash mid-write cannot truncate the
 * ledger. A corrupt file is treated as empty rather than preventing the plugin from enabling.</p>
 */
public final class DomainFloorRestoreLedger {

    /** One block to hand back: packed coordinates and the exact pre-floor block state. */
    public record Block(int x, int y, int z, String data) { }

    /** All pending blocks belonging to one domain floor session. */
    public record Entry(UUID worldId, UUID sessionId, List<Block> blocks) { }

    private static final int MAGIC = 0x44465231; // "DFR1"
    private static final int VERSION = 1;
    private static final int BLOCKS_PER_TICK = 512;
    /** Start as soon as the server ticks so stale originals cannot be overwritten by a new floor. */
    private static final long REPLAY_DELAY_TICKS = 1L;
    private static final int MAX_LEDGER_ENTRIES = 10_000;
    private static final int MAX_LEDGER_BLOCKS = 200_000;
    private static final int MAX_CHUNK_ATTEMPTS = 3;
    /** ~30 seconds at 20 TPS; the ledger is kept for the next enable if replay cannot finish. */
    private static final int MAX_REPLAY_TICKS = 600;
    /** At most one replay driver per JVM, so repeated failures cannot stack duplicate tasks. */
    private static final AtomicBoolean REPLAYING = new AtomicBoolean();

    private final File file;

    public DomainFloorRestoreLedger(File dataFolder) {
        this.file = new File(new File(dataFolder, "domain"), "floor-restore.dat");
    }

    /** Visible for tests and for the replay driver. */
    public File file() {
        return file;
    }

    public synchronized boolean isEmpty() {
        return !file.isFile();
    }

    /**
     * Collects the blocks of every column that is still unrestored. A change with no recorded
     * original was never written to the world and therefore needs no hand-back.
     */
    static List<Block> collectPending(int[] columnX, int[] columnZ, int[] changeStart,
                                       int[] changeY, String[] originalData,
                                       boolean[] columnRestored, int columnCount) {
        List<Block> pending = new ArrayList<>();
        if (columnX == null || columnZ == null || changeStart == null || changeY == null
                || originalData == null || columnRestored == null) return pending;
        for (int column = 0; column < columnCount && column < columnRestored.length; column++) {
            if (columnRestored[column] || column >= columnX.length || column >= columnZ.length) continue;
            int start = changeStart[column];
            int end = column + 1 < changeStart.length ? changeStart[column + 1] : originalData.length;
            for (int i = start; i < end && i < originalData.length && i < changeY.length; i++) {
                if (originalData[i] == null) continue;
                pending.add(new Block(columnX[column], changeY[i], columnZ[column], originalData[i]));
            }
        }
        return pending;
    }

    private static boolean matches(Entry entry, UUID sessionId, UUID worldId) {
        return sessionId != null && worldId != null
                && sessionId.equals(entry.sessionId()) && worldId.equals(entry.worldId());
    }

    /**
     * Merges blocks into the entry for this (session, world) pair. Matching on session alone would
     * replay world A's coordinates into world B when one caster had two pending floors (review H3).
     *
     * @return true when the ledger was written successfully
     */
    public synchronized boolean appendMerge(UUID sessionId, UUID worldId, List<Block> blocks) {
        if (sessionId == null || worldId == null || blocks == null || blocks.isEmpty()) return false;
        List<Entry> entries = new ArrayList<>(readAll());
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (matches(entries.get(i), sessionId, worldId)) {
                index = i;
                break;
            }
        }
        Map<Block, Block> merged = new LinkedHashMap<>();
        if (index >= 0) {
            for (Block block : entries.get(index).blocks()) merged.put(block, block);
        }
        for (Block block : blocks) merged.put(block, block);
        Entry replacement = new Entry(worldId, sessionId, List.copyOf(merged.values()));
        if (index >= 0) entries.set(index, replacement);
        else entries.add(replacement);
        return writeAll(entries);
    }

    /** Removes a batch of blocks from one entry; one write regardless of batch size. */
    public synchronized boolean removeBlocks(UUID sessionId, UUID worldId, List<Block> blocks) {
        if (sessionId == null || worldId == null || blocks == null || blocks.isEmpty()) return false;
        List<Entry> entries = new ArrayList<>(readAll());
        boolean changed = false;
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry entry = entries.get(i);
            if (!matches(entry, sessionId, worldId)) continue;
            List<Block> remaining = new ArrayList<>(entry.blocks());
            remaining.removeAll(blocks);
            if (remaining.size() != entry.blocks().size()) changed = true;
            if (remaining.isEmpty()) entries.remove(i);
            else entries.set(i, new Entry(entry.worldId(), entry.sessionId(), List.copyOf(remaining)));
        }
        return changed && writeAll(entries);
    }

    public synchronized boolean remove(UUID sessionId, UUID worldId) {
        if (sessionId == null || worldId == null) return false;
        List<Entry> entries = new ArrayList<>(readAll());
        boolean changed = entries.removeIf(entry -> matches(entry, sessionId, worldId));
        return changed && writeAll(entries);
    }

    public synchronized List<Entry> readAll() {
        if (!file.isFile()) return List.of();
        List<Entry> entries = new ArrayList<>();
        try (InputStream raw = Files.newInputStream(file.toPath());
             DataInputStream in = new DataInputStream(raw)) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) return List.of();
            int entryCount = in.readInt();
            // A corrupt count must never allocate an unbounded list during enable (review L2).
            if (entryCount < 0 || entryCount > MAX_LEDGER_ENTRIES) return List.of();
            for (int i = 0; i < entryCount; i++) {
                UUID worldId = UUID.fromString(in.readUTF());
                UUID sessionId = UUID.fromString(in.readUTF());
                int blockCount = in.readInt();
                if (blockCount < 0 || blockCount > MAX_LEDGER_BLOCKS) return List.of();
                List<Block> blocks = new ArrayList<>(blockCount);
                for (int b = 0; b < blockCount; b++) {
                    blocks.add(new Block(in.readInt(), in.readInt(), in.readInt(), in.readUTF()));
                }
                entries.add(new Entry(worldId, sessionId, List.copyOf(blocks)));
            }
            return entries;
        } catch (IOException | RuntimeException e) {
            return List.of();
        }
    }

    /** @return true when the ledger is now on disk (or intentionally empty). */
    private boolean writeAll(List<Entry> entries) {
        if (entries.isEmpty()) {
            try {
                Files.deleteIfExists(file.toPath());
                return true;
            } catch (IOException e) {
                return false;
            }
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        File temp = new File(parent, file.getName() + ".tmp");
        try (OutputStream raw = Files.newOutputStream(temp.toPath());
             DataOutputStream out = new DataOutputStream(raw)) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(entries.size());
            for (Entry entry : entries) {
                out.writeUTF(entry.worldId().toString());
                out.writeUTF(entry.sessionId().toString());
                out.writeInt(entry.blocks().size());
                for (Block block : entry.blocks()) {
                    out.writeInt(block.x());
                    out.writeInt(block.y());
                    out.writeInt(block.z());
                    out.writeUTF(block.data() == null ? "" : block.data());
                }
            }
            out.flush();
        } catch (IOException e) {
            return false;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Enable-time replay
    // ------------------------------------------------------------------

    private static volatile BukkitTask replayTask;
    /** True while a replay driver is active; callers can gate new domain floors on it. */
    public static boolean isReplaying() {
        return REPLAYING.get();
    }

    /** Cancels a live replay driver and releases the latch (shutdown / PlugMan reload). */
    public static void cancelReplay() {
        REPLAYING.set(false);
        BukkitTask task = replayTask;
        replayTask = null;
        if (task != null) {
            try { task.cancel(); } catch (RuntimeException ignored) { }
        }
    }

    /** Replays any ledger left by a previous shutdown. Safe to call at every enable. */
    public static void replay(Plugin plugin, Logger logger) {
        replay(plugin, logger, plugin.getDataFolder());
    }

    private record Key(UUID sessionId, UUID worldId) { }

    /** Test seam: replays a ledger at an explicit data folder. */
    public static void replay(Plugin plugin, Logger logger, File dataFolder) {
        DomainFloorRestoreLedger ledger = new DomainFloorRestoreLedger(dataFolder);
        if (ledger.isEmpty()) return;
        if (!REPLAYING.compareAndSet(false, true)) return;
        logger.info("Replaying " + ledger.readAll().size()
                + " pending domain floor restore(s) from the previous shutdown.");
        BukkitRunnable driver = new BukkitRunnable() {
            /** Chunk key to the number of load attempts already made. */
            private final Map<Long, Integer> inFlight = new HashMap<>();
            /** Chunks whose retry budget is exhausted; skipped until the next enable. */
            private final Set<Long> givenUp = new HashSet<>();
            /** Blocks written on the main thread, batched into one ledger rewrite per key per tick. */
            private final Map<Key, List<Block>> completed = new HashMap<>();
            private int ticks;

            @Override
            public void run() {
                // ONE batched rewrite per (session, world) instead of one per block (review H1).
                for (Map.Entry<Key, List<Block>> done : completed.entrySet()) {
                    ledger.removeBlocks(done.getKey().sessionId(), done.getKey().worldId(), done.getValue());
                }
                completed.clear();

                List<Entry> entries = new ArrayList<>(ledger.readAll());
                if (entries.isEmpty()) {
                    finish(logger, "Domain floor restore replay complete.");
                    return;
                }
                if (++ticks > MAX_REPLAY_TICKS) {
                    logger.warning("Domain floor restore replay gave up after " + ticks
                            + " ticks; remaining blocks stay in the ledger for the next enable.");
                    finish(logger, null);
                    return;
                }

                int processed = 0;
                for (Entry entry : entries) {
                    if (processed >= BLOCKS_PER_TICK) break; // global budget, not per entry (NEW-4)
                    World world = Bukkit.getWorld(entry.worldId());
                    if (world == null) continue; // world not loaded yet; retry next tick
                    Key key = new Key(entry.sessionId(), entry.worldId());
                    for (Block block : entry.blocks()) {
                        if (processed >= BLOCKS_PER_TICK) break;
                        int chunkX = block.x() >> 4;
                        int chunkZ = block.z() >> 4;
                        long chunk = ((long) chunkX << 32) | (chunkZ & 0xFFFF_FFFFL);
                        if (!world.isChunkLoaded(chunkX, chunkZ)) {
                            if (givenUp.contains(chunk)) continue;
                            int attempt = inFlight.getOrDefault(chunk, 0) + 1;
                            inFlight.put(chunk, attempt);
                            if (attempt > MAX_CHUNK_ATTEMPTS) {
                                logger.warning("Giving up on chunk " + chunkX + "," + chunkZ
                                        + " for a domain floor restore; its blocks stay in the ledger.");
                                inFlight.remove(chunk);
                                givenUp.add(chunk); // no 4-tick retry cycle (NEW-3)
                                continue;
                            }
                            try {
                                world.getChunkAtAsync(chunkX, chunkZ).whenComplete((loaded, error) ->
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (error != null) {
                                                inFlight.remove(chunk); // allow a bounded retry next tick
                                                logger.warning("Could not reload chunk " + chunkX + ","
                                                        + chunkZ + " for a domain floor restore: "
                                                        + error.getMessage());
                                                return;
                                            }
                                            inFlight.remove(chunk);
                                            if (writeBlock(logger, world, block)) {
                                                completed.computeIfAbsent(key, ignored -> new ArrayList<>())
                                                        .add(block);
                                            }
                                        }));
                            } catch (RuntimeException e) {
                                inFlight.remove(chunk);
                                logger.log(Level.WARNING,
                                        "Could not schedule a chunk reload for a domain floor restore", e);
                            }
                            continue;
                        }
                        if (writeBlock(logger, world, block)) {
                            completed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(block);
                            processed++;
                        }
                    }
                }
                if (ledger.isEmpty()) {
                    // The batched rewrite above may have drained it; apply pending completions now.
                    for (Map.Entry<Key, List<Block>> done : completed.entrySet()) {
                        ledger.removeBlocks(done.getKey().sessionId(), done.getKey().worldId(), done.getValue());
                    }
                    completed.clear();
                    if (ledger.isEmpty()) finish(logger, "Domain floor restore replay complete.");
                }
            }

            private void finish(Logger logger, String message) {
                if (message != null) logger.info(message);
                REPLAYING.set(false);
                replayTask = null;
                cancel();
            }
        };
        replayTask = driver.runTaskTimer(plugin, REPLAY_DELAY_TICKS, REPLAY_DELAY_TICKS);
    }

    /**
     * Writes one block. An unusable state string can never be restored, so it is logged once, loudly,
     * and dropped from the ledger rather than retried forever (which would keep the replay gate and
     * domain casts blocked for the whole tick budget). Every other block is written normally.
     */
    private static boolean writeBlock(Logger logger, World world, Block block) {
        try {
            world.getBlockAt(block.x(), block.y(), block.z())
                    .setBlockData(Bukkit.createBlockData(block.data()), false);
        } catch (IllegalArgumentException e) {
            logger.log(Level.WARNING, "Unknown block state '" + block.data() + "' at "
                    + block.x() + "," + block.y() + "," + block.z()
                    + "; dropping that pending domain floor block.", e);
        }
        return true;
    }
}
