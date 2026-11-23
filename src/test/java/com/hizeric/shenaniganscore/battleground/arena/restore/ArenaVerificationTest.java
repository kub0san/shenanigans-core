package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.Policy;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.TicketLedger;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaRestoreEngine.Deps;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaVerification.Source;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure verification decisions: no server, no Bukkit, no files. */
class ArenaVerificationTest {

    private static final ChunkPos CHUNK = new ChunkPos(0, 0);
    private static final World WORLD = fakeWorld();
    private static final Chunk FAKE_CHUNK = fakeChunk();

    @SuppressWarnings("unchecked")
    private static World fakeWorld() {
        return (World) Proxy.newProxyInstance(ArenaVerificationTest.class.getClassLoader(),
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
        return (Chunk) Proxy.newProxyInstance(ArenaVerificationTest.class.getClassLoader(),
                new Class<?>[]{Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeChunk";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** Two snapshot chunks (x=0..17 at y=60), so one job per chunk is observable. */
    private static ArenaSnapshot chunkGrid() {
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        for (ChunkPos pos : List.of(new ChunkPos(0, 0), new ChunkPos(1, 0))) {
            int[] cells = new int[ArenaSnapshot.cellCount(0, 60, 0, 17, 60, 0, pos.x(), pos.z())];
            Arrays.fill(cells, 1);
            chunks.put(pos, cells);
        }
        return new ArenaSnapshot("ffa", "world", 0, 60, 0, 17, 60, 0,
                1L, 18L, "TEST", List.of("minecraft:air", "minecraft:stone"), chunks);
    }

    private static DiffLog.Decoded emptyDiff(boolean complete) {
        return new DiffLog.Decoded("ffa", 1L, complete, Set.of(), null);
    }

    private static ArenaRegion region() {
        return new ArenaRegion(UUID.randomUUID(), "world",
                new BlockVector(0, 60, 0), new BlockVector(17, 60, 0), 0L, 1L, "TEST");
    }

    private static ArenaRestoreEngine engine(boolean verify, List<Long> writes) {
        Deps deps = new Deps(
                (world, cx, cz) -> CompletableFuture.completedFuture(FAKE_CHUNK),
                task -> task.run(),
                (world, packed, state, physics) -> writes.add(packed),
                (world, x, y, z) -> "minecraft:stone",
                () -> new Policy(100_000, 1, 4, 8, false, verify, false),
                new TicketLedger<>(key -> { }, key -> { }),
                WORLD);
        return new ArenaRestoreEngine(deps, Logger.getLogger("ArenaVerificationTest"), null,
                () -> 0L);
    }

    /** 2x2 arena, y=60..61; stone floor at y=60, air above. */
    private static ArenaSnapshot snapshot() {
        int[] cells = new int[ArenaSnapshot.cellCount(0, 60, 0, 1, 61, 1, 0, 0)];
        for (int z = 0; z <= 1; z++) {
            for (int x = 0; x <= 1; x++) {
                cells[(0 * 2 + z) * 2 + x] = 1;
            }
        }
        return new ArenaSnapshot("ffa", "world", 0, 60, 0, 1, 61, 1,
                7L, 4L, "TEST", List.of("minecraft:air", "minecraft:stone"),
                Map.of(CHUNK, cells));
    }

    /** All-stone 16x32x16 chunk (8192 cells) bundled with a probe that counts its calls. */
    private static final class LargeChunk {
        final ArenaSnapshot snapshot;
        final CountingProbe probe;
        LargeChunk(ArenaSnapshot snapshot) {
            this.snapshot = snapshot;
            this.probe = new CountingProbe(snapshot);
        }
    }

    /** Returns each cell's baseline state while recording how often it was asked. */
    private static final class CountingProbe implements ArenaVerification.StateProbe {
        private final ArenaSnapshot snapshot;
        int calls;
        CountingProbe(ArenaSnapshot snapshot) { this.snapshot = snapshot; }
        @Override public String stateAt(int x, int y, int z) {
            calls++;
            return snapshot.stateAt(x, y, z);
        }
    }

    private static LargeChunk largeChunk() {
        int[] cells = new int[ArenaSnapshot.cellCount(0, 60, 0, 15, 91, 15, 0, 0)];
        Arrays.fill(cells, 1); // palette index 1 = minecraft:stone
        ArenaSnapshot snapshot = new ArenaSnapshot("ffa", "world", 0, 60, 0, 15, 91, 15,
                1L, cells.length, "TEST", List.of("minecraft:air", "minecraft:stone"),
                Map.of(CHUNK, cells));
        return new LargeChunk(snapshot);
    }

    @Test
    void byChunkCoversEverySnapshotPosition() {
        Map<ChunkPos, Set<Long>> positions = ArenaVerification.byChunk(snapshot());
        assertEquals(Set.of(CHUNK), positions.keySet());
        assertEquals(8, positions.get(CHUNK).size());
        assertTrue(positions.get(CHUNK).contains(BlockKey.pack(1, 61, 1)));
    }

    @Test
    void untrustedWhenWorldDiffersOutsideTheLedger() {
        long changed = BlockKey.pack(0, 60, 0);
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 0 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);

        assertEquals(Set.of(changed),
                ArenaVerification.unloggedChunk(snapshot, CHUNK, world, Set.of()));
        assertEquals(Set.of(),
                ArenaVerification.unloggedChunk(snapshot, CHUNK, world, Set.of(changed)),
                "a ledger entry makes the discrepancy trusted");
        assertEquals(Set.of(),
                ArenaVerification.unloggedChunk(snapshot, CHUNK, snapshot::stateAt, Set.of()),
                "a clean world has no discrepancies");
    }

    @Test
    void sourceForIsPerChunkAndHonoursBothToggles() {
        long changed = BlockKey.pack(0, 60, 0);
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 0 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);
        Map<ChunkPos, Set<Long>> unlogged = Map.of(CHUNK,
                ArenaVerification.unloggedChunk(snapshot, CHUNK, world, Set.of()));
        Set<ChunkPos> untrusted = ArenaVerification.untrustedChunks(unlogged);

        assertEquals(Set.of(CHUNK), untrusted);
        assertFalse(ArenaVerification.trusted(CHUNK, untrusted));
        assertTrue(ArenaVerification.trusted(new ChunkPos(1, 1), untrusted));

        assertEquals(Source.SNAPSHOT, ArenaVerification.sourceFor(CHUNK, true, true, untrusted));
        assertEquals(Source.DIFF, ArenaVerification.sourceFor(CHUNK, true, false, untrusted),
                "fallback=false makes verification diagnostic-only");
        assertEquals(Source.DIFF, ArenaVerification.sourceFor(CHUNK, false, true, untrusted),
                "verify=false trusts the ledger unconditionally");
        assertEquals(Source.DIFF, ArenaVerification.sourceFor(new ChunkPos(1, 1), true, true, untrusted));
    }

    /** RED (b): an empty committed diff still verifies every chunk present in the snapshot. */
    @Test
    void emptyDiffStillVerifiesEverySnapshotChunk() {
        List<Long> writes = new ArrayList<>();
        ArenaRestoreEngine engine = engine(true, writes);
        ArenaSnapshot arena = chunkGrid();

        assertTrue(engine.beginRestore(region(), "ffa", emptyDiff(true), arena));
        for (int i = 0; i < 100 && engine.isRestoring(); i++) engine.restoreStep(null);

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertEquals(arena.chunkPositions().size(), engine.verifiedChunks(),
                "every snapshot chunk needs a verification job even with an empty ledger");
        assertTrue(writes.isEmpty(), "verification-only jobs never write blocks");
    }

    /** RED (Step 3): an unflushed diff forces verification even when the toggle is off. */
    @Test
    void unflushedDiffForcesVerificationEvenWithoutTheToggle() {
        List<Long> writes = new ArrayList<>();
        ArenaRestoreEngine engine = engine(false, writes);
        ArenaSnapshot arena = chunkGrid();

        assertTrue(engine.beginRestore(region(), "ffa", emptyDiff(false), arena));
        for (int i = 0; i < 100 && engine.isRestoring(); i++) engine.restoreStep(null);

        assertFalse(engine.isRestoring());
        assertEquals(arena.chunkPositions().size(), engine.verifiedChunks(),
                "!diff.complete() must override restoreVerify=false");
        assertTrue(writes.isEmpty());
    }

    /** An empty committed ledger must not freeze progress at 0% while verification jobs run. */
    @Test void emptyCommittedLedgerProgressIsNotStuckAtZero() {
        List<Long> writes = new ArrayList<>();
        ArenaRestoreEngine engine = engine(true, writes);
        assertTrue(engine.beginRestore(region(), "ffa", emptyDiff(true), chunkGrid()));
        engine.restoreStep(null);
        assertTrue(engine.progressPercent() > 0, "an empty ledger must not freeze progress at 0%");
        for (int i = 0; i < 100 && engine.isRestoring(); i++) engine.restoreStep(null);
        assertEquals(100, engine.progressPercent());
    }

    @Test
    void sampledUnloggedChunkDetectsDiscrepancyAtFirstCell() {
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 0 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);

        assertEquals(Set.of(BlockKey.pack(0, 60, 0)), ArenaVerification.sampledUnloggedChunk(
                snapshot, CHUNK, world, Set.of(), ArenaVerification.CHEAP_SAMPLE_CELLS));
    }

    @Test
    void sampledUnloggedChunkIgnoresLedgerPositions() {
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 0 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);

        assertEquals(Set.of(), ArenaVerification.sampledUnloggedChunk(
                snapshot, CHUNK, world, Set.of(BlockKey.pack(0, 60, 0)),
                ArenaVerification.CHEAP_SAMPLE_CELLS));
    }

    @Test
    void sampledUnloggedChunkTreatsNullWorldStateAsDiscrepancy() {
        ArenaSnapshot snapshot = snapshot();
        ArenaVerification.StateProbe world = (x, y, z) -> null;

        assertTrue(ArenaVerification.sampledUnloggedChunk(snapshot, CHUNK, world, Set.of(),
                        ArenaVerification.CHEAP_SAMPLE_CELLS).contains(BlockKey.pack(0, 60, 0)),
                "a null world state must not throw and the first sampled cell must be reported");
    }

    @Test
    void sampledUnloggedChunkProbesAtMostSampleCells() {
        LargeChunk large = largeChunk();

        assertEquals(Set.of(), ArenaVerification.sampledUnloggedChunk(
                large.snapshot, CHUNK, large.probe, Set.of(), 64));
        assertTrue(large.probe.calls <= 64, "at most sampleCells probe calls");
        assertTrue(large.probe.calls >= 2, "a large chunk must still get several probes");
        assertTrue(large.probe.calls < 8192 / 10, "far fewer calls than an exhaustive 8192-cell scan");
    }

    @Test
    void sampledUnloggedChunkDoesNotReportCellsOutsideSample() {
        ArenaSnapshot snapshot = largeChunk().snapshot;
        ArenaVerification.StateProbe world = (x, y, z) ->
                x == 1 && y == 60 && z == 0 ? "minecraft:dirt" : snapshot.stateAt(x, y, z);

        assertEquals(Set.of(), ArenaVerification.sampledUnloggedChunk(
                snapshot, CHUNK, world, Set.of(), 64),
                "cell index 1 is off the stride, so the bounded detector accepts the residual risk");
    }

    @Test
    void sampledUnloggedChunkReportsDirtAtTheLastCell() {
        ArenaSnapshot snapshot = largeChunk().snapshot;
        int lastX = ArenaSnapshot.CHUNK_SIZE - 1; // (15, 91, 15) is the last cell of the 8192-cell chunk
        int lastY = snapshot.maxY();
        int lastZ = ArenaSnapshot.CHUNK_SIZE - 1;
        ArenaVerification.StateProbe world = (x, y, z) ->
                (x == lastX && y == lastY && z == lastZ) ? "minecraft:dirt" : snapshot.stateAt(x, y, z);

        assertTrue(ArenaVerification.sampledUnloggedChunk(snapshot, CHUNK, world, Set.of(), 64)
                .contains(BlockKey.pack(lastX, lastY, lastZ)),
                "a dirt probe at the last sampled cell must be reported");
    }

    @Test
    void sampledUnloggedChunkOnEmptySnapshotReturnsEmpty() {
        ArenaVerification.StateProbe probe = (x, y, z) -> "minecraft:stone";

        assertEquals(Set.of(), ArenaVerification.sampledUnloggedChunk(null, CHUNK, probe, null, 64));
    }
}
