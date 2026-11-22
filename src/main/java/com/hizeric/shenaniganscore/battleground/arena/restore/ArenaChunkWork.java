package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Pure budget, ticket and write-policy seams shared by the tiered restore engine.
 *
 * <p>Nothing here touches Bukkit: the engine supplies the real clock, ticket callbacks and world
 * probes, while the tests drive the same code with fakes.</p>
 */
public final class ArenaChunkWork {

    /** Two sections per chunk, the section-cap ruling carried forward from the SDD ledger. */
    public static final int SECTIONS_PER_CHUNK_CAP = 2;
    /** The design's "small radius" deferral call, in blocks. */
    public static final double DEFER_RADIUS_BLOCKS = 12.0;

    private static final List<String> LIGHT_OR_FLUID = List.of(
            "water", "lava", "light", "torch", "lantern", "glow", "fire", "flame",
            "beacon", "end_rod", "campfire", "magma", "bubble_column", "sculk");

    private ArenaChunkWork() { }

    /** Snapshot of the live restore tuning, derived once per restore step. */
    public record Policy(int msPerTick, int blocksPerTick, int chunksPerTick, int sectionsPerTick,
                         boolean relight, boolean verify, boolean fallback, boolean deferNearPlayer) {

        public static Policy from(ArenaRuntimeConfig config) {
            ArenaRuntimeConfig live = config == null ? ArenaRuntimeConfig.defaults() : config;
            int chunks = Math.max(1, live.restoreChunksPerTick());
            return new Policy(
                    Math.max(1, live.restoreMsPerTick()),
                    Math.max(1, live.restoreBlocksPerTick()),
                    chunks,
                    chunks * SECTIONS_PER_CHUNK_CAP,
                    live.restoreRelight(),
                    live.restoreVerify(),
                    live.restoreFallbackOnUnverified(),
                    live.restoreDeferNearPlayer());
        }
    }

    /** Per-tick millisecond guard plus block, chunk and section caps. */
    public static final class WorkBudget {
        private final long maxNanos;
        private final int blockCap;
        private final int chunkCap;
        private final LongSupplier nanoClock;
        private long tickStart;
        private int blocks;
        private int chunks;

        public WorkBudget(int msPerTick, int blocksPerTick, int chunksPerTick, LongSupplier nanoClock) {
            this.maxNanos = Math.max(1, msPerTick) * 1_000_000L;
            this.blockCap = Math.max(1, blocksPerTick);
            this.chunkCap = Math.max(1, chunksPerTick);
            this.nanoClock = nanoClock == null ? System::nanoTime : nanoClock;
        }

        public void beginTick() {
            tickStart = nanoClock.getAsLong();
            blocks = 0;
            chunks = 0;
        }

        public boolean canContinue() {
            return nanoClock.getAsLong() - tickStart < maxNanos;
        }

        public boolean canWriteBlock() { return blocks < blockCap && canContinue(); }
        public void recordBlock() { blocks++; }

        public boolean canStartChunk() { return chunks < chunkCap && canContinue(); }
        public void recordChunk() { chunks++; }

        public boolean canStartSections(int count) {
            return chunks + Math.max(1, count) <= chunkCap && canContinue();
        }

        public int blocksThisTick() { return blocks; }
        public int chunksThisTick() { return chunks; }
    }

    /** A ticket ledger that acquires and releases each key exactly once. */
    public static final class TicketLedger<K> {
        private final Consumer<K> acquire;
        private final Consumer<K> release;
        private final Set<K> held = new LinkedHashSet<>();

        public TicketLedger(Consumer<K> acquire, Consumer<K> release) {
            this.acquire = acquire;
            this.release = release;
        }

        public void acquire(K key) {
            if (key != null && held.add(key)) acquire.accept(key);
        }

        public void release(K key) {
            if (key != null && held.remove(key)) release.accept(key);
        }

        public void releaseAll() {
            for (K key : new ArrayList<>(held)) release.accept(key);
            held.clear();
        }

        public int heldCount() { return held.size(); }
        public boolean holds(K key) { return held.contains(key); }
    }

    /** One block write descriptor. Bulk writes are physics-free; relight re-applies fluids. */
    public record Write(long packed, String state, boolean applyPhysics) {
        public static Write bulk(long packed, String state) { return new Write(packed, state, false); }
        public static Write relight(long packed, String state) { return new Write(packed, state, true); }
    }

    /** True for fluid and light-emitting block states that need a relight nudge. */
    public static boolean needsRelight(String state) {
        if (state == null || state.isEmpty()) return false;
        int bracket = state.indexOf('[');
        String type = (bracket < 0 ? state : state.substring(0, bracket))
                .toLowerCase(Locale.ROOT);
        for (String keyword : LIGHT_OR_FLUID) {
            if (type.contains(keyword)) return true;
        }
        return false;
    }

    /** Relight targets are derived only from baseline states, and only when the toggle is on. */
    public static Set<Long> relightTargets(Set<Long> positions,
                                           Function<Long, String> baselineState,
                                           boolean enabled) {
        Set<Long> targets = new LinkedHashSet<>();
        if (!enabled || positions == null || baselineState == null) return targets;
        for (long packed : positions) {
            if (needsRelight(baselineState.apply(packed))) targets.add(packed);
        }
        return targets;
    }

    /** Pure coordinate form of "defer cells near a live player" (inclusive boundary). */
    public static boolean nearPlayer(int x, int z, List<int[]> players, double radius) {
        if (players == null || players.isEmpty()) return false;
        double radiusSquared = radius * radius;
        for (int[] player : players) {
            double dx = x - player[0];
            double dz = z - player[1];
            if (dx * dx + dz * dz <= radiusSquared) return true;
        }
        return false;
    }
}
