package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRestorer;
import com.hizeric.shenaniganscore.battleground.arena.RestoreBudget;
import com.hizeric.shenaniganscore.battleground.arena.diff.DiffLog;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.Policy;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.TicketLedger;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaRestoreEngine.Deps;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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

/** Proof that the restorer interface remains source-compatible for the tiered engine. */
class ArenaRestorerInterfaceTest {

    @Test
    void newMethodsAreDefaultsOnArenaRestorer() throws Exception {
        Method progress = ArenaRestorer.class.getMethod("progressPercent");
        assertTrue(progress.isDefault());
        assertEquals(int.class, progress.getReturnType());

        Method begin = ArenaRestorer.class.getMethod("beginRestore", ArenaRegion.class,
                String.class, DiffLog.Decoded.class, ArenaSnapshot.class);
        assertTrue(begin.isDefault());
        assertEquals(boolean.class, begin.getReturnType());
    }

    @Test
    void legacyMethodsRemainAndTieredEngineImplementsTheInterface() throws Exception {
        ArenaRestorer.class.getMethod("id");
        ArenaRestorer.class.getMethod("prepare", ArenaRegion.class);
        ArenaRestorer.class.getMethod("capture", ArenaRegion.class);
        ArenaRestorer.class.getMethod("beginRestore", ArenaRegion.class);
        ArenaRestorer.class.getMethod("restoreStep", RestoreBudget.class);
        ArenaRestorer.class.getMethod("cancel");
        ArenaRestorer.class.getMethod("isRestoring");
        ArenaRestorer.class.getMethod("restoreFailed");

        assertTrue(ArenaRestorer.class.isAssignableFrom(ArenaRestoreEngine.class));
    }

    @Test
    void newDefaultBeginRestoreDelegatesToTheLegacyOverload() {
        LegacyRestorer legacy = new LegacyRestorer();
        DiffLog.Decoded diff = new DiffLog.Decoded("ffa", 1L, true, Set.of(), null);

        assertTrue(legacy.beginRestore(null, "ffa", diff, null));
        assertTrue(legacy.legacyBeginCalled);
        assertEquals(0, legacy.progressPercent(), "legacy restorers report no real progress");
    }

    /**
     * Issue #3 owns {@code progressPercent()}; this guards the API boundary it will extend. The
     * trusted-diff fast path must keep the existing written-ledger progress contract: every step
     * reports a percent within [0,100], and a clean finish reports 100 with no failure. Whether
     * verification earns its own progress slice is deliberately not asserted here — that is #3's
     * decision.
     */
    @Test
    void progressContractSurvivesTrustedDiffFastPath() throws Exception {
        Method progress = ArenaRestorer.class.getMethod("progressPercent");
        assertTrue(progress.isDefault(), "issue #3 extends a default method, not a new accessor");

        ArenaRestoreEngine engine = engine();
        Set<Long> ledger = Set.of(BlockKey.pack(1, 60, 1), BlockKey.pack(2, 60, 1));
        DiffLog.Decoded completeDiff = new DiffLog.Decoded("ffa", 1L, true, ledger, null);

        assertTrue(engine.beginRestore(region(), "ffa", completeDiff, stoneChunks()));
        for (int i = 0; i < 500 && engine.isRestoring(); i++) {
            engine.restoreStep(null);
            int percent = engine.progressPercent();
            assertTrue(percent >= 0 && percent <= 100,
                    "progress must stay in [0,100], was " + percent + " at step " + i);
        }

        assertFalse(engine.isRestoring());
        assertFalse(engine.restoreFailed());
        assertEquals(100, engine.progressPercent());
    }

    @SuppressWarnings("unchecked")
    private static World fakeWorld() {
        return (World) Proxy.newProxyInstance(ArenaRestorerInterfaceTest.class.getClassLoader(),
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
        return (Chunk) Proxy.newProxyInstance(ArenaRestorerInterfaceTest.class.getClassLoader(),
                new Class<?>[]{Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "toString" -> "FakeChunk";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    /** Local mirror of {@code ArenaRestoreEngineTest.engine(...)} — no cross-test imports. */
    private static ArenaRestoreEngine engine() {
        // Post-#6 seven-component Policy; no defer-near-player argument.
        Policy policy = new Policy(100_000, 2, 4, 8, false, true, false);
        Chunk chunk = fakeChunk();
        Deps deps = new Deps(
                (world, cx, cz) -> CompletableFuture.completedFuture(chunk),
                task -> task.run(),
                (world, packed, state, physics) -> { },
                (world, x, y, z) -> "minecraft:stone",
                () -> policy,
                new TicketLedger<>(key -> { }, key -> { }),
                fakeWorld());
        return new ArenaRestoreEngine(deps,
                Logger.getLogger("ArenaRestorerInterfaceTest"), null, () -> 0L);
    }

    /** Single-layer, one-chunk all-stone snapshot spanning x/z 0..15 at y=60. */
    private static ArenaSnapshot stoneChunks() {
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        int[] data = new int[ArenaSnapshot.cellCount(0, 60, 0, 15, 60, 15, 0, 0)];
        Arrays.fill(data, 1);
        chunks.put(new ChunkPos(0, 0), data);
        return new ArenaSnapshot("ffa", "world", 0, 60, 0, 15, 60, 15,
                1L, chunks.size(), "TEST", List.of("minecraft:air", "minecraft:stone"), chunks);
    }

    private static ArenaRegion region() {
        return new ArenaRegion(UUID.randomUUID(), "world",
                new BlockVector(0, 60, 0), new BlockVector(31, 60, 15), 0L, 1L, "TEST");
    }

    /** A legacy-style implementation that only knows the legacy overload. */
    private static final class LegacyRestorer implements ArenaRestorer {
        private boolean legacyBeginCalled;

        @Override public String id() { return "legacy"; }
        @Override public boolean prepare(ArenaRegion region) { return true; }
        @Override public boolean capture(ArenaRegion region) { return true; }
        @Override public boolean beginRestore(ArenaRegion region) {
            legacyBeginCalled = true;
            return true;
        }
        @Override public boolean restoreStep(RestoreBudget budget) { return true; }
        @Override public void cancel() { }
        @Override public boolean isRestoring() { return false; }
    }
}
