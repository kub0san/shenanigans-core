package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;

/**
 * Pure, read-only completeness verification.
 *
 * <p>For a chunk, the discrepancies are {@code {positions where world != baseline} - ledger}.
 * A non-empty remainder makes that chunk untrusted. The scan never cancels events and never
 * writes; the engine decides what to do with the result.</p>
 *
 * <p>{@link ChunkScan} is the important addition. The old budgeted scan stopped early and the
 * engine still marked the chunk "verified", so a big chunk could be trusted after only a partial
 * look. A {@code ChunkScan} remembers where it stopped, so the engine can resume next tick and only
 * reach a verdict once every cell has really been compared. It also walks coordinates directly
 * instead of first building a ~100k-element boxed {@code Set<Long>} for every chunk.</p>
 */
public final class ArenaVerification {

    /** Upper bound on cell probes when the caller does not override the sample size. */
    public static final int CHEAP_SAMPLE_CELLS = 64;

    /** Supplies the current world state string in the same form as {@code getAsString()}. */
    public interface StateProbe {
        String stateAt(int x, int y, int z);
    }

    public enum Source { DIFF, SNAPSHOT }

    private ArenaVerification() { }

    /** All snapshot positions, grouped by the snapshot's own chunk grid. */
    public static Map<ChunkPos, Set<Long>> byChunk(ArenaSnapshot snapshot) {
        Map<ChunkPos, Set<Long>> chunks = new TreeMap<>();
        if (snapshot == null) return chunks;
        for (ChunkPos pos : snapshot.chunkPositions()) {
            chunks.put(pos, positionsOf(snapshot, pos));
        }
        return chunks;
    }

    /** Every region-clipped position in one chunk, in packed-key form. */
    public static Set<Long> positionsOf(ArenaSnapshot snapshot, ChunkPos pos) {
        Set<Long> positions = new LinkedHashSet<>();
        if (snapshot == null || pos == null) return positions;
        int x0 = Math.max(snapshot.minX(), pos.x() * ArenaSnapshot.CHUNK_SIZE);
        int x1 = Math.min(snapshot.maxX(), pos.x() * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
        int z0 = Math.max(snapshot.minZ(), pos.z() * ArenaSnapshot.CHUNK_SIZE);
        int z1 = Math.min(snapshot.maxZ(), pos.z() * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
        for (int y = snapshot.minY(); y <= snapshot.maxY(); y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    positions.add(BlockKey.pack(x, y, z));
                }
            }
        }
        return positions;
    }

    /** World-vs-baseline discrepancies in one chunk that the ledger does not explain. */
    public static Set<Long> unloggedChunk(ArenaSnapshot snapshot, ChunkPos chunk,
                                          StateProbe world, Set<Long> ledger) {
        return unloggedChunk(snapshot, chunk, world, ledger, null);
    }

    /**
     * Budgeted variant: the scan stops as soon as {@code mayContinue} is false. The result may be
     * partial, so callers that need a verdict should use a {@link ChunkScan} and resume it.
     */
    public static Set<Long> unloggedChunk(ArenaSnapshot snapshot, ChunkPos chunk,
                                          StateProbe world, Set<Long> ledger,
                                          BooleanSupplier mayContinue) {
        if (snapshot == null || chunk == null || world == null) return new LinkedHashSet<>();
        ChunkScan scan = new ChunkScan(snapshot, chunk);
        scan.step(world, ledger, mayContinue, 0);
        return scan.unlogged();
    }

    /**
     * Bounded, read-only trusted-diff detector. Samples at most {@code sampleCells} evenly spaced
     * cells in one chunk and returns the sampled positions where the world disagrees with the
     * baseline and the ledger does not explain the difference. Cells are visited in the same
     * Y, Z, X order as {@link ChunkScan}; the last cell is always sampled so a trailing anomaly is
     * not lost to the stride arithmetic. One probe slot is reserved for that last cell, so a cell
     * count that divides evenly by {@code sampleCells} cannot push the total past the bound.
     *
     * <p>This is a detector, not an exhaustive verdict: a clean result does not prove the chunk is
     * trusted, because an anomaly between two sample points is missed by design. Callers must
     * escalate any non-empty result to a full {@link ChunkScan} before acting on it. The scan never
     * writes and never cancels events.</p>
     */
    public static Set<Long> sampledUnloggedChunk(ArenaSnapshot snapshot, ChunkPos chunk,
                                                 StateProbe world, Set<Long> ledger,
                                                 int sampleCells) {
        Set<Long> unlogged = new LinkedHashSet<>();
        if (snapshot == null || chunk == null || world == null) return unlogged;
        int x0 = Math.max(snapshot.minX(), chunk.x() * ArenaSnapshot.CHUNK_SIZE);
        int x1 = Math.min(snapshot.maxX(), chunk.x() * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
        int z0 = Math.max(snapshot.minZ(), chunk.z() * ArenaSnapshot.CHUNK_SIZE);
        int z1 = Math.min(snapshot.maxZ(), chunk.z() * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
        if (x1 < x0 || z1 < z0) return unlogged;
        Set<Long> logged = ledger == null ? Set.of() : ledger;
        long spanX = (long) x1 - x0 + 1L;
        long spanZ = (long) z1 - z0 + 1L;
        long spanY = (long) snapshot.maxY() - snapshot.minY() + 1L;
        long totalCells = spanX * spanZ * spanY;
        int last = (int) (totalCells - 1L);
        if (last < 0) return unlogged;
        int budget = Math.max(1, sampleCells);
        int stride = (int) Math.max(1L, totalCells / budget);
        int probes = 0;
        int index = 0;
        for (int y = snapshot.minY(); y <= snapshot.maxY(); y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    boolean sampled = index % stride == 0 || index == last;
                    boolean room = index == last ? probes < budget : probes < budget - 1;
                    if (sampled && room) {
                        probes++;
                        if (!Objects.equals(snapshot.stateAt(x, y, z), world.stateAt(x, y, z))) {
                            long packed = BlockKey.pack(x, y, z);
                            if (!logged.contains(packed)) unlogged.add(packed);
                        }
                    }
                    index++;
                }
            }
        }
        return unlogged;
    }

    /**
     * Resumable world-vs-baseline comparison of one chunk. Cells are visited in the snapshot's own
     * Y, Z, X order. Not thread-safe; the engine drives it from the main thread only.
     */
    public static final class ChunkScan {
        private final ArenaSnapshot snapshot;
        private final int x0, x1, z0, z1, y1;
        private final Set<Long> unlogged = new LinkedHashSet<>();
        private int x, y, z;
        private boolean done;

        public ChunkScan(ArenaSnapshot snapshot, ChunkPos chunk) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(chunk, "chunk");
            this.x0 = Math.max(snapshot.minX(), chunk.x() * ArenaSnapshot.CHUNK_SIZE);
            this.x1 = Math.min(snapshot.maxX(), chunk.x() * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
            this.z0 = Math.max(snapshot.minZ(), chunk.z() * ArenaSnapshot.CHUNK_SIZE);
            this.z1 = Math.min(snapshot.maxZ(), chunk.z() * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
            this.y1 = snapshot.maxY();
            this.x = x0;
            this.y = snapshot.minY();
            this.z = z0;
            this.done = x1 < x0 || z1 < z0;
        }

        /**
         * Advances the scan.
         *
         * @param guaranteedBlocks cells compared before {@code mayContinue} is consulted at all,
         *                         so a nearly exhausted tick budget still makes forward progress
         * @return true when every cell has been compared (the verdict is final)
         */
        public boolean step(StateProbe world, Set<Long> ledger, BooleanSupplier mayContinue,
                            int guaranteedBlocks) {
            if (done) return true;
            Objects.requireNonNull(world, "world");
            Set<Long> logged = ledger == null ? Set.of() : ledger;
            int sinceCheck = 0;
            int budgetFree = Math.max(0, guaranteedBlocks);
            while (!done) {
                if (budgetFree > 0) {
                    budgetFree--;
                } else if (mayContinue != null && (sinceCheck++ & 63) == 0
                        && !mayContinue.getAsBoolean()) {
                    return false;
                }
                if (!java.util.Objects.equals(snapshot.stateAt(x, y, z), world.stateAt(x, y, z))) {
                    long packed = BlockKey.pack(x, y, z);
                    if (!logged.contains(packed)) unlogged.add(packed);
                }
                if (++x > x1) {
                    x = x0;
                    if (++z > z1) {
                        z = z0;
                        if (++y > y1) done = true;
                    }
                }
            }
            return true;
        }

        public boolean done() { return done; }

        /** Discrepancies found so far; final once {@link #done()} is true. */
        public Set<Long> unlogged() { return unlogged; }
    }

    public static Set<ChunkPos> untrustedChunks(Map<ChunkPos, Set<Long>> unlogged) {
        Set<ChunkPos> untrusted = new LinkedHashSet<>();
        if (unlogged == null) return untrusted;
        for (Map.Entry<ChunkPos, Set<Long>> entry : unlogged.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                untrusted.add(entry.getKey());
            }
        }
        return untrusted;
    }

    public static boolean trusted(ChunkPos chunk, Set<ChunkPos> untrusted) {
        return untrusted == null || !untrusted.contains(chunk);
    }

    /** Verification disabled => trust the ledger; fallback disabled => diagnostic only. */
    public static Source sourceFor(ChunkPos chunk, boolean verify, boolean fallback,
                                   Set<ChunkPos> untrusted) {
        if (!verify || !fallback) return Source.DIFF;
        return trusted(chunk, untrusted) ? Source.DIFF : Source.SNAPSHOT;
    }
}
