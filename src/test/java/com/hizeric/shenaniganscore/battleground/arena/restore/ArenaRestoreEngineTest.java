package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.Policy;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.TicketLedger;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaRestoreEngine.Deps;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct engine-level regressions for the Task 6 review findings. No server: every Bukkit side
 * effect is injected through {@link Deps}, and the same production dispatch/write path is driven.
 */
class ArenaRestoreEngineTest {

    private static final Logger LOG = Logger.getLogger("ArenaRestoreEngineTest");
    private static final World WORLD = fakeWorld();
    private static final Chunk CHUNK = fakeChunk();
    private static final ArenaRestoreEngine.MainRunner SYNC = task -> task.run();
    private static final ArenaRestoreEngine.ChunkLoader OK_LOADER =
            (world, cx, cz) -> CompletableFuture.completedFuture(CHUNK);
    private static final ArenaRestoreEngine.StateProbe STONE =
            (world, x, y, z) -> "minecraft:stone";

    @SuppressWarnings("unchecked")
    private static World fakeWorld() {
        return (World) Proxy.newProxyInstance(ArenaRestoreEngineTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "test-world";
                    case "getPlayers" -> List.of();
                    case "isChunkLoaded" -> true;
                    case "toString" -> "FakeWorld";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static Chunk fakeChunk() {
        return (Chunk) Proxy.newProxyInstance(ArenaRestoreEngineTest.class.getClassLoader(),
                new Class<?>[]{Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeChunk";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static Player playerProxy(int x, int z) {
        return (Player) Proxy.newProxyInstance(ArenaRestoreEngineTest.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isDead" -> false;
                    case "getLocation" -> new Location(WORLD, x, 60, z);
                    case "toString" -> "FakePlayer(" + x + "," + z + ")";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** Forced world with the same probe cases as fakeWorld plus a live player view. */
    @SuppressWarnings("unchecked")
    private static World worldWithPlayers(List<int[]> players) {
        return (World) Proxy.newProxyInstance(ArenaRestoreEngineTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "test-world";
                    case "getPlayers" -> {
                        List<Player> result = new ArrayList<>(players.size());
                        for (int[] player : players) result.add(playerProxy(player[0], player[1]));
                        yield result;
                    }
                    case "isChunkLoaded" -> true;
                    case "toString" -> "FakeWorld";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static World worldWithPlayers(int... xz) {
        List<int[]> players = new ArrayList<>();
        for (int i = 0; i + 1 < xz.length; i += 2) players.add(new int[]{xz[i], xz[i + 1]});
        return worldWithPlayers(players);
    }

    /** Everything the fakes observe during a run. */
    private static final class Fixture {
        final List<Long> writes = new ArrayList<>();
        final List<Long> physics = new ArrayList<>();
        final Deque<Runnable> tasks = new ArrayDeque<>();
    }

    private static ArenaRestoreEngine engine(Fixture fx, Policy policy,
                                             ArenaRestoreEngine.MainRunner runner,
                                             ArenaRestoreEngine.ChunkLoader loader,
                                             ArenaRestoreEngine.StateProbe probe) {
        return engine(fx, policy, runner, loader, probe, (Runnable) null, WORLD);
    }

    private static ArenaRestoreEngine engine(Fixture fx, Policy policy,
                                             ArenaRestoreEngine.MainRunner runner,
                                             ArenaRestoreEngine.ChunkLoader loader,
                                             ArenaRestoreEngine.StateProbe probe,
                                             Runnable onTierOneComplete) {
        return engine(fx, policy, runner, loader, probe, onTierOneComplete, WORLD);
    }

    private static ArenaRestoreEngine engine(Fixture fx, Policy policy,
                                             ArenaRestoreEngine.MainRunner runner,
                                             ArenaRestoreEngine.ChunkLoader loader,
                                             ArenaRestoreEngine.StateProbe probe,
                                             World forcedWorld) {
        return engine(fx, policy, runner, loader, probe, null, forcedWorld);
    }

    private static ArenaRestoreEngine engine(Fixture fx, Policy policy,
                                             ArenaRestoreEngine.MainRunner runner,
                                             ArenaRestoreEngine.ChunkLoader loader,
                                             ArenaRestoreEngine.StateProbe probe,
                                             Runnable onTierOneComplete, World forcedWorld) {
        Deps deps = new Deps(loader, runner,
                (world, packed, state, physics) -> {
                    fx.writes.add(packed);
                    if (physics) fx.physics.add(packed);
                },
                probe,
                () -> policy,
                new TicketLedger<>(key -> { }, key -> { }),
                forcedWorld);
        return new ArenaRestoreEngine(deps, LOG, onTierOneComplete, () -> 0L);
    }

    /** Single-layer all-stone snapshot spanning the requested chunk grid. */
    private static ArenaSnapshot stoneChunks(int maxX, int maxZ) {
        return stoneChunks(0, 60, maxX, maxZ);
    }

    /** All-stone snapshot with an explicit Y span, so a fallback chunk can be kept small. */
    private static ArenaSnapshot stoneChunks(int minY, int maxY, int maxX, int maxZ) {
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        for (int cz = 0; cz <= maxZ / 16; cz++) {
            for (int cx = 0; cx <= maxX / 16; cx++) {
                int[] data = new int[ArenaSnapshot.cellCount(0, minY, 0, maxX, maxY, maxZ, cx, cz)];
                Arrays.fill(data, 1);
                chunks.put(new ChunkPos(cx, cz), data);
            }
        }
        return new ArenaSnapshot("ffa", "world", 0, minY, 0, maxX, maxY, maxZ,
                1L, chunks.size(), "TEST", List.of("minecraft:air", "minecraft:stone"), chunks);
    }

    private static ArenaRegion region() {
        return new ArenaRegion(UUID.randomUUID(), "world",
                new BlockVector(0, 60, 0), new BlockVector(31, 60, 15), 0L, 1L, "TEST");
    }

    private static Set<Long> set(long... values) {
        Set<Long> result = new LinkedHashSet<>();
        for (long value : values) result.add(value);
        return result;
    }

    private static DiffLog.Decoded diff(Set<Long> positions) {
        return new DiffLog.Decoded("ffa", 1L, true, positions, null);
    }

    private static DiffLog.Decoded unflushedEmptyDiff() {
        return new DiffLog.Decoded("ffa", 2L, false, Set.of(), "unflushed round");
    }

    private static Policy policy(int blocksPerTick, boolean verify, boolean fallback) {
        return new Policy(100_000, blocksPerTick, 4, 8, false, verify, fallback);
    }

    private static void drive(ArenaRestoreEngine engine) {
        for (int i = 0; i < 500 && engine.isRestoring(); i++) engine.restoreStep(null);
    }

    /** Critical: a slice larger than the budget must re-queue its remainder, not drop it. */
    @Test
    void boundedSlicesWriteEveryPositionBeforeCompletion() {
        Fixture fx = new Fixture();
        ArenaRestoreEngine engine = engine(fx, policy(2, false, false), SYNC, OK_LOADER, STONE);
        Set<Long> positions = set(
                BlockKey.pack(1, 60, 1), BlockKey.pack(2, 60, 1), BlockKey.pack(4, 60, 1),
                BlockKey.pack(5, 60, 1), BlockKey.pack(7, 60, 1));

        assertTrue(engine.beginRestore(region(), "ffa", diff(positions), stoneChunks(15, 15)));
        assertTrue(engine.tierOneComplete(), "off-grid diff positions are not tier-1 essentials");
        drive(engine);

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertEquals(positions, Set.copyOf(fx.writes));
        assertEquals(positions.size(), fx.writes.size(), "no position may be dropped or duplicated");
        assertEquals(100, engine.progressPercent());
    }

    /** Decision test: a stationary player standing on a cell must not defer it. */
    @Test
    void stationaryPlayerOnCellStillGetsWritten() {
        Fixture fx = new Fixture();
        long underFeet = BlockKey.pack(1, 60, 1);
        long beside = BlockKey.pack(2, 60, 1);
        long far = BlockKey.pack(10, 60, 10);
        Set<Long> all = set(underFeet, beside, far);
        ArenaRestoreEngine engine = engine(fx, policy(2, false, false), SYNC, OK_LOADER, STONE,
                worldWithPlayers(1, 1)); // stationary for every step

        assertTrue(engine.beginRestore(region(), "ffa", diff(all), stoneChunks(15, 15)));
        drive(engine);

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertEquals(all, Set.copyOf(fx.writes), "no cell may stay deferred because a player stands still");
        assertEquals(all.size(), fx.writes.size(), "no cell written twice");
        assertEquals(100, engine.progressPercent(), "the bar reaches 100 while the player is stationary");
    }

    @Test
    void multiplePlayersDoNotCreateAPendingSet() {
        Fixture fx = new Fixture();
        long underA = BlockKey.pack(1, 60, 1);
        long underB = BlockKey.pack(10, 60, 10);
        long underC = BlockKey.pack(2, 60, 1);
        Set<Long> all = set(underA, underB, underC);
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false), SYNC, OK_LOADER, STONE,
                worldWithPlayers(1, 1, 10, 10, 2, 1));

        assertTrue(engine.beginRestore(region(), "ffa", diff(all), stoneChunks(15, 15)));
        drive(engine);

        assertFalse(engine.isRestoring());
        assertEquals(all, Set.copyOf(fx.writes), "three players must not leave three pending cells");
        assertEquals(100, engine.progressPercent());
    }

    @Test
    void playerLeavingMidRestoreWritesFromTheFirstStep() {
        Fixture fx = new Fixture();
        Set<Long> all = set(BlockKey.pack(1, 60, 1), BlockKey.pack(2, 60, 1), BlockKey.pack(1, 60, 2));
        List<int[]> players = new ArrayList<>();
        players.add(new int[]{1, 1}); // on the very first cell
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false), SYNC, OK_LOADER, STONE,
                worldWithPlayers(players));

        assertTrue(engine.beginRestore(region(), "ffa", diff(all), stoneChunks(15, 15)));
        engine.restoreStep(null);
        assertEquals(1, fx.writes.size(), "the first step must write a cell under a stationary player");

        players.clear(); // player leaves: the forced world's player view is rebuilt empty
        drive(engine);
        assertFalse(engine.isRestoring());
        assertEquals(all, Set.copyOf(fx.writes));
        assertEquals(100, engine.progressPercent());
    }

    @Test
    void inFlightCellsAreDiscardedAcrossReload() {
        Fixture fx = new Fixture();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        ArenaRestoreEngine.ChunkLoader holding = (world, cx, cz) -> {
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        };
        long oldCell = BlockKey.pack(1, 60, 1);
        long newCell = BlockKey.pack(2, 60, 2);
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false),
                task -> fx.tasks.add(task), holding, STONE, worldWithPlayers(1, 1));

        assertTrue(engine.beginRestore(region(), "ffa", diff(set(oldCell)), stoneChunks(15, 15)));
        engine.restoreStep(null);
        assertEquals(1, futures.size(), "the old generation has one in-flight load");

        engine.cancel();                 // plugin disable / reload
        assertTrue(engine.beginRestore(region(), "ffa", diff(set(newCell)), stoneChunks(15, 15)));
        futures.get(0).complete(CHUNK);
        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();
        assertFalse(fx.writes.contains(oldCell), "a stale deferred cell must never be written after reload");

        engine.restoreStep(null);
        assertEquals(2, futures.size(), "the new generation dispatches its own load");
        futures.get(1).complete(CHUNK);
        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();

        drive(engine);
        assertFalse(engine.isRestoring());
        assertEquals(Set.of(newCell), Set.copyOf(fx.writes));
    }

    /** Important: progress counts distinct ledger positions, never raw write count. */
    @Test
    void progressTracksDistinctLedgerPositions() {
        Fixture fx = new Fixture();
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false), SYNC, OK_LOADER, STONE);
        Set<Long> all = set(
                BlockKey.pack(0, 60, 0), BlockKey.pack(3, 60, 0),   // tier 1 (sampled columns)
                BlockKey.pack(6, 60, 0), BlockKey.pack(9, 60, 0),
                BlockKey.pack(1, 60, 1), BlockKey.pack(2, 60, 1),   // tier 2 (off-grid)
                BlockKey.pack(4, 60, 1));

        assertTrue(engine.beginRestore(region(), "ffa", diff(all), stoneChunks(15, 15)));
        assertEquals(0, engine.progressPercent());

        for (int i = 0; i < 100 && engine.isRestoring(); i++) {
            engine.restoreStep(null);
            long distinct = fx.writes.stream().distinct().count();
            int expected = (int) Math.min(100L, distinct * 100L / all.size());
            assertEquals(expected, engine.progressPercent(), "progress after step " + i);
            if (engine.isRestoring()) {
                assertTrue(engine.progressPercent() < 100, "no premature 100% while work remains");
            }
        }

        assertFalse(engine.isRestoring());
        assertEquals(all, Set.copyOf(fx.writes));
        assertEquals(all.size(), fx.writes.size());
        assertEquals(100, engine.progressPercent());
    }

    /** Important: the tier-1 gate must wait for every tier-1 chunk, not the first final slice. */
    @Test
    void tierOneGateWaitsForAllTierOneChunks() {
        Fixture fx = new Fixture();
        int[] gateCalls = {0};
        // One chunk per tick keeps the shared block budget per chunk; the three-position chunk
        // still needs two slices, so its first slice is a per-job non-final slice with a remainder.
        Policy oneChunkPerTick = new Policy(100_000, 2, 1, 2, false, false, false);
        ArenaRestoreEngine engine = engine(fx, oneChunkPerTick,
                task -> fx.tasks.add(task), OK_LOADER, STONE, () -> gateCalls[0]++);
        long solo = BlockKey.pack(0, 60, 0);       // chunk (0,0): a single-slice tier-1 chunk
        long chunkAA = BlockKey.pack(18, 60, 0);
        long chunkAB = BlockKey.pack(21, 60, 0);
        long chunkARemainder = BlockKey.pack(24, 60, 0); // chunk (1,0): two tier-1 slices
        Set<Long> chunkA = set(chunkAA, chunkAB, chunkARemainder);
        Set<Long> all = set(solo, chunkAA, chunkAB, chunkARemainder);

        assertTrue(engine.beginRestore(region(), "ffa", diff(all), stoneChunks(60, 60, 31, 15)));
        assertFalse(engine.tierOneComplete(), "two tier-1 chunks are pending");
        assertEquals(0, gateCalls[0]);

        boolean sawSolo = false;
        boolean sawPartialChunk = false;
        int callbacks = 0;
        for (int step = 0; step < 10 && !engine.tierOneComplete(); step++) {
            engine.restoreStep(null);
            assertEquals(1, fx.tasks.size(), "one tier-1 chunk per tick");
            fx.tasks.poll().run();
            callbacks++;

            Set<Long> written = Set.copyOf(fx.writes);
            sawSolo |= written.contains(solo);
            sawPartialChunk |= chunkA.stream().filter(written::contains).count() == 2;
            if (!written.equals(all)) {
                assertFalse(engine.tierOneComplete(),
                        "gate must stay closed until every tier-1 position is written: " + written);
                assertEquals(0, gateCalls[0]);
            }
        }

        assertTrue(sawSolo, "the single-slice tier-1 chunk was dispatched");
        assertTrue(sawPartialChunk, "the other chunk re-queued a tier-1 remainder");
        assertEquals(3, callbacks, "single-slice chunk plus two slices of the other chunk");
        assertEquals(all, Set.copyOf(fx.writes), "every tier-1 position is written last");
        assertTrue(engine.tierOneComplete(), "gate opens only after the last tier-1 position");
        assertEquals(1, gateCalls[0]);
    }

    /** Important: fail() must stop already-dispatched callbacks before they write anything. */
    @Test
    void failStopsInFlightCallbacksBeforeTheyWrite() {
        Fixture fx = new Fixture();
        int[] loads = {0};
        ArenaRestoreEngine.ChunkLoader failingSecondLoad = (world, cx, cz) -> {
            if (loads[0]++ == 1) throw new IllegalStateException("second load failed");
            return CompletableFuture.completedFuture(CHUNK);
        };
        ArenaRestoreEngine engine = engine(fx, policy(2, false, false),
                task -> fx.tasks.add(task), failingSecondLoad, STONE);
        Set<Long> all = set(
                BlockKey.pack(1, 60, 1), BlockKey.pack(2, 60, 1),
                BlockKey.pack(17, 60, 1), BlockKey.pack(18, 60, 1));

        assertTrue(engine.beginRestore(region(), "ffa", diff(all), stoneChunks(31, 15)));
        assertTrue(engine.restoreStep(null), "the load failure ends the restore");
        assertTrue(engine.restoreFailed());
        assertEquals(0, engine.progressPercent(), "a failed restore never reports 100%");

        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();
        assertTrue(fx.writes.isEmpty(), "an in-flight callback must not write after fail()");
    }

    /** Important: whole-chunk fallback is bounded by blocksPerTick and re-queues the remainder. */
    @Test
    void fullChunkFallbackIsBoundedAndRequeues() {
        Fixture fx = new Fixture();
        ArenaRestoreEngine.StateProbe unloggedProbe = (world, x, y, z) ->
                (x == 1 && y == 60 && z == 1) ? "minecraft:dirt" : "minecraft:stone";
        ArenaRestoreEngine engine = engine(fx, policy(4, true, true), SYNC, OK_LOADER, unloggedProbe);
        Set<Long> ledger = set(
                BlockKey.pack(2, 60, 1), BlockKey.pack(4, 60, 1),
                BlockKey.pack(5, 60, 1), BlockKey.pack(7, 60, 1));

        ArenaSnapshot snapshot = stoneChunks(60, 60, 15, 15);
        assertTrue(engine.beginRestore(region(), "ffa", diff(ledger), snapshot));
        int chunkPositions = ArenaVerification.positionsOf(snapshot, new ChunkPos(0, 0)).size();
        int maxPerStep = 0;
        for (int i = 0; i < 500 && engine.isRestoring(); i++) {
            int before = fx.writes.size();
            engine.restoreStep(null);
            maxPerStep = Math.max(maxPerStep, fx.writes.size() - before);
        }

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertEquals(1, engine.untrustedChunks());
        assertTrue(maxPerStep <= 4, "one slice may never exceed the 4-block budget, was " + maxPerStep);
        assertEquals(chunkPositions, fx.writes.stream().distinct().count(),
                "the whole snapshot remainder is eventually written");
        assertEquals(chunkPositions, fx.writes.size(), "no fallback position written twice");
    }

    /**
     * I-WB1: a tier-1 chunk with an unlogged discrepancy must still be detected. With fallback
     * enabled the remainder escalates to a snapshot tier-2 restore, but the tier-1 gate opens as
     * soon as the tier-1 slice is written and never waits on verification/tier-2.
     */
    @Test
    void tierOneUnloggedDiscrepancyEscalatesWithoutBlockingGate() {
        Fixture fx = new Fixture();
        int[] gateCalls = {0};
        ArenaRestoreEngine.StateProbe unloggedProbe = (world, x, y, z) ->
                (x == 1 && y == 60 && z == 1) ? "minecraft:dirt" : "minecraft:stone";
        ArenaRestoreEngine engine = engine(fx, policy(4, true, true), SYNC, OK_LOADER,
                unloggedProbe, () -> gateCalls[0]++);
        long tierOne = BlockKey.pack(0, 60, 0); // sampled grid surface -> tier-1 essential
        ArenaSnapshot snapshot = stoneChunks(60, 60, 15, 15);
        int chunkPositions = ArenaVerification.positionsOf(snapshot, new ChunkPos(0, 0)).size();

        assertTrue(engine.beginRestore(region(), "ffa", diff(set(tierOne)), snapshot));
        engine.restoreStep(null);

        assertTrue(engine.tierOneComplete(), "tier-1 gate must not wait on verification/tier-2");
        assertEquals(1, gateCalls[0], "tier-1 gate fires once when the tier-1 slice is written");
        assertTrue(engine.isRestoring(), "escalated tier-2 snapshot work should still be pending");
        assertEquals(1, engine.untrustedChunks(), "tier-1 chunk discrepancy must be detected");

        drive(engine);

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertEquals(1, gateCalls[0], "tier-1 gate must not refire");
        assertEquals(chunkPositions, fx.writes.stream().distinct().count(),
                "escalated snapshot remainder is eventually written");
        assertEquals(chunkPositions, fx.writes.size(), "no position written twice");
    }

    /**
     * I-WB1: with fallback disabled, a tier-1 unlogged discrepancy is diagnostic only; the chunk
     * is not snapshot-escalated, but the discrepancy is still detected and the gate still opens.
     */
    @Test
    void tierOneUnloggedDiscrepancyDiagnosticOnlyWhenFallbackDisabled() {
        Fixture fx = new Fixture();
        int[] gateCalls = {0};
        ArenaRestoreEngine.StateProbe unloggedProbe = (world, x, y, z) ->
                (x == 1 && y == 60 && z == 1) ? "minecraft:dirt" : "minecraft:stone";
        ArenaRestoreEngine engine = engine(fx, policy(4, true, false), SYNC, OK_LOADER,
                unloggedProbe, () -> gateCalls[0]++);
        long tierOne = BlockKey.pack(0, 60, 0);

        assertTrue(engine.beginRestore(region(), "ffa", diff(set(tierOne)), stoneChunks(60, 60, 15, 15)));
        drive(engine);

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertTrue(engine.tierOneComplete(), "tier-1 gate must still open");
        assertEquals(1, gateCalls[0]);
        assertEquals(1, engine.untrustedChunks(), "discrepancy must be detected even without fallback");
        assertEquals(Set.of(tierOne), Set.copyOf(fx.writes),
                "diagnostic-only mode must not snapshot-escalate tier-1 chunk");
        assertEquals(1, fx.writes.size(), "no duplicate writes");
    }

    /**
     * I-WB3: a tier-1 unlogged discrepancy with fallback enabled must not re-enqueue tier-2 work
     * after cancelTierTwo(). Otherwise jobsEmpty() becomes false while next() refuses tier-2,
     * stalling restore forever.
     */
    @Test
    void tierOneDiscrepancyAfterCancelTierTwoDoesNotStallRestore() {
        Fixture fx = new Fixture();
        int[] gateCalls = {0};
        ArenaRestoreEngine.StateProbe unloggedProbe = (world, x, y, z) ->
                (x == 1 && y == 60 && z == 1) ? "minecraft:dirt" : "minecraft:stone";
        ArenaRestoreEngine engine = engine(fx, policy(4, true, true), SYNC, OK_LOADER,
                unloggedProbe, () -> gateCalls[0]++);
        long tierOne = BlockKey.pack(0, 60, 0); // sampled grid surface -> tier-1 essential

        assertTrue(engine.beginRestore(region(), "ffa", diff(set(tierOne)), stoneChunks(60, 60, 15, 15)));
        engine.cancelTierTwo();
        drive(engine);

        assertFalse(engine.isRestoring(), "cancelled tier-2 escalation must not stall restore");
        assertFalse(engine.restoreFailed());
        assertTrue(engine.tierOneComplete());
        assertEquals(1, gateCalls[0]);
        assertEquals(1, engine.untrustedChunks(), "discrepancy is still detected");
        assertEquals(Set.of(tierOne), Set.copyOf(fx.writes),
                "cancelled tier-2 must not be re-enqueued by tier-1 escalation");
        assertEquals(1, fx.writes.size(), "no duplicate writes");
    }

    /** RED (a): the section cap used to be checked against the chunk cap, starving dispatch. */
    @Test
    void chunksPerTickDispatchesExactlyThatManyChunks() {
        for (int chunksPerTick : new int[]{1, 4}) {
            Fixture fx = new Fixture();
            int[] loads = {0};
            ArenaRestoreEngine.ChunkLoader counting = (world, cx, cz) -> {
                loads[0]++;
                return CompletableFuture.completedFuture(CHUNK);
            };
            Policy policy = new Policy(100_000, 1, chunksPerTick,
                    chunksPerTick * ArenaChunkWork.SECTIONS_PER_CHUNK_CAP,
                    false, false, false);
            ArenaRestoreEngine engine = engine(fx, policy, task -> { }, counting, STONE);
            Set<Long> ledger = new LinkedHashSet<>();
            for (int cz = 0; cz < 4; cz++) {
                for (int cx = 0; cx < 4; cx++) {
                    // stay off the stride-3 deployment grid in every chunk
                    int ox = cx == 2 ? 2 : 1;
                    int oz = cz == 2 ? 2 : 1;
                    ledger.add(BlockKey.pack(cx * 16 + ox, 60, cz * 16 + oz));
                }
            }

            assertTrue(engine.beginRestore(region(), "ffa", diff(ledger),
                    stoneChunks(60, 60, 63, 63)));
            assertTrue(engine.tierOneComplete(), "off-grid diff cells are never tier-1 essentials");
            engine.restoreStep(null);

            assertEquals(chunksPerTick, loads[0],
                    "chunksPerTick=" + chunksPerTick + " must dispatch exactly that many chunks");
        }
    }

    /** RED (c): a callback from an abandoned generation must not reach processChunk. */
    @Test
    void callbackFromPreviousGenerationIsIgnored() {
        Fixture fx = new Fixture();
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        ArenaRestoreEngine.ChunkLoader holding = (world, cx, cz) -> {
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        };
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false),
                task -> fx.tasks.add(task), holding, STONE);
        long old = BlockKey.pack(1, 60, 1);
        long latest = BlockKey.pack(2, 60, 2);

        assertTrue(engine.beginRestore(region(), "ffa", diff(set(old)), stoneChunks(15, 15)));
        engine.restoreStep(null);
        assertEquals(1, futures.size(), "the first generation has one in-flight load");

        engine.cancel();
        assertTrue(engine.beginRestore(region(), "ffa", diff(set(latest)), stoneChunks(15, 15)));

        futures.get(0).complete(CHUNK);
        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();
        assertFalse(fx.writes.contains(old), "the stale callback must not apply its old dispatch");

        engine.restoreStep(null);
        assertEquals(2, futures.size(), "the new generation dispatches its own load");
        futures.get(1).complete(CHUNK);
        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();
        assertEquals(Set.of(latest), Set.copyOf(fx.writes), "only the new generation is applied");
    }

    /**
     * CRITICAL: a tier-2 boundary cancel while a chunk load is in flight must still finish when
     * that last callback drains pendingLoads — the stepper may already be stopped by the caller.
     */
    @Test
    void cancelTierTwoWithInFlightLoadFinishesOnCallback() {
        Fixture fx = new Fixture();
        int[] finishes = {0};
        boolean[] failed = {true};
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        ArenaRestoreEngine.ChunkLoader holding = (world, cx, cz) -> {
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        };
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false),
                task -> fx.tasks.add(task), holding, STONE);
        engine.effects(new RestoreEffects() {
            @Override public void onFinished(World world, boolean wasFailed) {
                finishes[0]++;
                failed[0] = wasFailed;
            }
        });

        assertTrue(engine.beginRestore(region(), "ffa",
                diff(set(BlockKey.pack(1, 60, 1))), stoneChunks(15, 15)));
        assertTrue(engine.tierOneComplete(), "an off-grid ledger has no tier-1 work");
        engine.restoreStep(null);
        assertEquals(1, futures.size(), "the tier-2 chunk load is in flight");

        engine.cancelTierTwo();
        assertTrue(engine.isRestoring(), "the in-flight load still owns completion");

        futures.get(0).complete(CHUNK);
        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();

        assertFalse(engine.isRestoring(), "the last callback must finish a tier-2 cancelled restore");
        assertFalse(engine.restoreFailed());
        assertEquals(1, finishes[0], "finish effect fires exactly once");
        assertFalse(failed[0], "a boundary cancel is not a failure");
        assertTrue(fx.writes.isEmpty(), "a cancelled tier-2 chunk must not be written");
    }

    /**
     * Invariant: a full cancel fires finish once, and its stale same-generation in-flight callback
     * must not fire it again (cancel() leaves phase IDLE, which finish()'s own guard cannot catch).
     */
    @Test
    void fullCancelStaleCallbackDoesNotRefireFinish() {
        Fixture fx = new Fixture();
        int[] finishes = {0};
        List<CompletableFuture<Chunk>> futures = new ArrayList<>();
        ArenaRestoreEngine.ChunkLoader holding = (world, cx, cz) -> {
            CompletableFuture<Chunk> future = new CompletableFuture<>();
            futures.add(future);
            return future;
        };
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false),
                task -> fx.tasks.add(task), holding, STONE);
        engine.effects(new RestoreEffects() {
            @Override public void onFinished(World world, boolean wasFailed) { finishes[0]++; }
        });

        assertTrue(engine.beginRestore(region(), "ffa",
                diff(set(BlockKey.pack(1, 60, 1))), stoneChunks(15, 15)));
        engine.restoreStep(null);
        assertEquals(1, futures.size());
        long before = engine.generation();

        engine.cancel();
        assertEquals(1, finishes[0], "full cancel fires finish once");

        futures.get(0).complete(CHUNK);
        while (!fx.tasks.isEmpty()) fx.tasks.poll().run();

        assertEquals(before, engine.generation(), "full cancel leaves the generation unchanged");
        assertEquals(1, finishes[0], "a stale callback must not refire finish after cancel()");
        assertFalse(engine.isRestoring());
        assertTrue(fx.writes.isEmpty(), "a cancelled callback must not write");
    }

    /** RED (d): cancel() must end the cosmetic layer so a boss bar cannot leak on disable. */
    @Test
    void cancelFiresFinishEffect() {
        Fixture fx = new Fixture();
        int[] finishes = {0};
        boolean[] failed = {true};
        ArenaRestoreEngine engine = engine(fx, policy(1, false, false), SYNC, OK_LOADER, STONE);
        engine.effects(new RestoreEffects() {
            @Override public void onFinished(World world, boolean wasFailed) {
                finishes[0]++;
                failed[0] = wasFailed;
            }
        });

        assertTrue(engine.beginRestore(region(), "ffa",
                diff(set(BlockKey.pack(1, 60, 1))), stoneChunks(15, 15)));
        engine.cancel();
        assertEquals(1, finishes[0], "cancel fires the finish effect exactly once");
        assertFalse(failed[0], "a deliberate cancel is not a failure");
        assertFalse(engine.isRestoring());
    }

    /**
     * RED (Step 3): relight's second write is budgeted work, not a free extra write. Diff positions
     * are an unordered set ({@code DiffLog.Decoded} normalises with {@code Set.copyOf}), so which of
     * the two cells reaches the dispatch first is deliberately unspecified; only the per-tick
     * budget shape below is deterministic.
     */
    @Test
    void relightSecondWriteIsBilledToTheBlockBudget() {
        Fixture fx = new Fixture();
        Policy policy = new Policy(100_000, 2, 4, 8, true, false, false);
        ArenaRestoreEngine engine = engine(fx, policy, SYNC, OK_LOADER, STONE);
        long first = BlockKey.pack(1, 60, 1);
        long second = BlockKey.pack(2, 60, 1);
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        int[] cells = new int[ArenaSnapshot.cellCount(0, 60, 0, 15, 60, 15, 0, 0)];
        Arrays.fill(cells, 1);
        chunks.put(new ChunkPos(0, 0), cells);
        ArenaSnapshot water = new ArenaSnapshot("ffa", "world", 0, 60, 0, 15, 60, 15,
                1L, cells.length, "TEST", List.of(ArenaSnapshot.AIR, "minecraft:water"), chunks);

        assertTrue(engine.beginRestore(region(), "ffa", diff(set(first, second)), water));
        engine.restoreStep(null);

        assertEquals(2, fx.writes.size(), "one bulk write plus one relight write in this tick");
        assertEquals(1, fx.physics.size(), "only the relight re-apply uses physics");
        long relitCell = fx.physics.get(0);
        assertTrue(Set.of(first, second).contains(relitCell),
                "relight must target a ledger cell, was " + relitCell);
        assertEquals(1, Set.copyOf(fx.writes).size(),
                "one cell is written bulk+relight; the other waits for the next tick: " + fx.writes);
        assertEquals(relitCell, fx.writes.get(0).longValue(),
                "the relight must re-apply the cell that was bulk-written");

        drive(engine);
        assertFalse(engine.isRestoring());
        assertEquals(Set.of(first, second), Set.copyOf(fx.writes));
        assertEquals(4, fx.writes.size(), "each cell is written bulk then relight");
    }

    /** An empty ledger must report work-share progress instead of freezing at 0%. */
    @Test void emptyUnflushedLedgerProgressAdvancesInsteadOfStayingAtZero() {
        Fixture fx = new Fixture();
        Policy oneChunk = new Policy(100_000, 256, 1, 2, false, true, false);
        ArenaRestoreEngine engine = engine(fx, oneChunk, SYNC, OK_LOADER, STONE);
        assertTrue(engine.beginRestore(region(), "ffa", unflushedEmptyDiff(),
                stoneChunks(60, 60, 31, 15))); // two snapshot chunks
        assertEquals(0, engine.progressPercent());
        engine.restoreStep(null);
        assertEquals(50, engine.progressPercent(), "one of two verification chunks is done");
        drive(engine);
        assertFalse(engine.isRestoring());
        assertEquals(100, engine.progressPercent());
    }

    /** The reported symptom is the boss bar, so assert the cosmetic layer sees increasing percents. */
    @Test void bossBarProgressLayerReceivesIncreasingPercentsForEmptyLedger() {
        Fixture fx = new Fixture();
        List<Integer> seen = new ArrayList<>();
        Policy oneChunk = new Policy(100_000, 256, 1, 2, false, true, false);
        ArenaRestoreEngine engine = engine(fx, oneChunk, SYNC, OK_LOADER, STONE);
        engine.effects(new RestoreEffects() {
            @Override public void onProgress(World world, int percent) { seen.add(percent); }
        });
        assertTrue(engine.beginRestore(region(), "ffa", unflushedEmptyDiff(),
                stoneChunks(60, 60, 79, 79))); // 25 chunks -> effects fire at steps 10 and 20
        drive(engine);
        assertTrue(seen.size() >= 2, "effects must fire more than once, was " + seen);
        assertTrue(seen.get(0) > 0, "the bar must never be driven to a hard 0%, was " + seen);
        assertTrue(seen.get(1) > seen.get(0), "reported progress must increase, was " + seen);
        assertEquals(100, engine.progressPercent());
    }

    /** A failed empty-ledger restore must never cap out at 100%. */
    @Test void emptyLedgerFailedRestoreNeverReportsComplete() {
        Fixture fx = new Fixture();
        int[] loads = {0};
        ArenaRestoreEngine.ChunkLoader failingFirst = (w, cx, cz) -> {
            if (loads[0]++ == 0) throw new IllegalStateException("load failed");
            return CompletableFuture.completedFuture(CHUNK);
        };
        ArenaRestoreEngine engine = engine(fx, policy(4, true, false), SYNC, failingFirst, STONE);
        assertTrue(engine.beginRestore(region(), "ffa", unflushedEmptyDiff(),
                stoneChunks(60, 61, 15, 15)));
        assertTrue(engine.restoreStep(null));
        assertTrue(engine.restoreFailed());
        assertTrue(engine.progressPercent() < 100, "a failed empty-ledger restore must never show 100%");
    }

    /** Non-empty ledgers keep the distinct-position formula; fallback writes cannot inflate it. */
    @Test void nonEmptyLedgerIgnoresWorkAccountingAndFallbackWrites() {
        Fixture fx = new Fixture();
        ArenaRestoreEngine.StateProbe unlogged = (w, x, y, z) ->
                (x == 1 && y == 60 && z == 1) ? "minecraft:dirt" : "minecraft:stone";
        ArenaRestoreEngine engine = engine(fx, policy(4, true, true), SYNC, OK_LOADER, unlogged);
        Set<Long> ledger = set(BlockKey.pack(2, 60, 1), BlockKey.pack(4, 60, 1),
                BlockKey.pack(5, 60, 1), BlockKey.pack(7, 60, 1));
        assertTrue(engine.beginRestore(region(), "ffa", diff(ledger),
                stoneChunks(60, 61, 15, 15)));
        for (int i = 0; i < 100 && engine.isRestoring(); i++) {
            engine.restoreStep(null);
            long distinct = fx.writes.stream().filter(ledger::contains).distinct().count();
            assertEquals((int) Math.min(100L, distinct * 100L / ledger.size()),
                    engine.progressPercent(),
                    "fallback cells outside the ledger must not inflate progress");
        }
        assertEquals(100, engine.progressPercent());
    }
}
