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
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #17 follow-up (independent review H1): a burst of stale load callbacks landing in one tick
 * must share ONE bounded callback budget. Giving each callback a fresh allowance would multiply the
 * configured per-tick block/ms caps by the number of in-flight loads.
 */
class BurstCallbackBudgetTest {

    private static final Logger LOG = Logger.getLogger("BurstCallbackBudgetTest");

    @SuppressWarnings("unchecked")
    private static World world() {
        return (World) Proxy.newProxyInstance(BurstCallbackBudgetTest.class.getClassLoader(),
                new Class<?>[]{World.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "test-world";
                    case "getPlayers" -> List.of();
                    case "isChunkLoaded" -> true;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    @SuppressWarnings("unchecked")
    private static Chunk chunk() {
        return (Chunk) Proxy.newProxyInstance(BurstCallbackBudgetTest.class.getClassLoader(),
                new Class<?>[]{Chunk.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> null;
                });
    }

    private static ArenaSnapshot twoChunkSnapshot() {
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        for (ChunkPos pos : List.of(new ChunkPos(0, 0), new ChunkPos(1, 0))) {
            int[] data = new int[ArenaSnapshot.cellCount(0, 60, 0, 31, 60, 15, pos.x(), pos.z())];
            Arrays.fill(data, 1);
            chunks.put(pos, data);
        }
        return new ArenaSnapshot("ffa", "world", 0, 60, 0, 31, 60, 15,
                1L, chunks.size(), "TEST", List.of("minecraft:air", "minecraft:stone"), chunks);
    }

    @Test
    void staleCallbacksInTheSameTickShareOneBoundedBudget() {
        World world = world();
        Chunk chunk = chunk();
        AtomicLong clock = new AtomicLong(0L);
        Deque<Runnable> callbacks = new ArrayDeque<>();
        List<Long> writes = new ArrayList<>();
        int blockCap = 3;
        Policy policy = new Policy(1, blockCap, 8, 16, false, false, false);
        Deps deps = new Deps((w, cx, cz) -> CompletableFuture.completedFuture(chunk),
                callbacks::addLast,
                (w, packed, state, physics) -> writes.add(packed),
                (w, x, y, z) -> "minecraft:stone", () -> policy,
                new TicketLedger<>(key -> { }, key -> { }), world);
        ArenaRestoreEngine engine = new ArenaRestoreEngine(deps, LOG, null, clock::get);

        Set<Long> positions = Set.of(
                BlockKey.pack(1, 60, 1), BlockKey.pack(2, 60, 1),
                BlockKey.pack(3, 60, 1), BlockKey.pack(4, 60, 1),
                BlockKey.pack(17, 60, 1), BlockKey.pack(18, 60, 1),
                BlockKey.pack(19, 60, 1), BlockKey.pack(20, 60, 1));
        assertTrue(engine.beginRestore(
                new ArenaRegion(UUID.randomUUID(), "world",
                        new org.bukkit.util.BlockVector(0, 60, 0),
                        new org.bukkit.util.BlockVector(31, 60, 15), 0L, 1L, "TEST"),
                "ffa", new DiffLog.Decoded("ffa", 1L, true, positions, null), twoChunkSnapshot()));
        assertFalse(engine.restoreStep(null));
        assertEquals(2, callbacks.size(), "both chunk loads must have been dispatched");

        // Both callbacks land later, in the same wall-clock tick, with stale dispatch budgets.
        clock.set(10_000_000L);
        callbacks.pollFirst().run();
        callbacks.pollFirst().run();

        assertTrue(writes.size() <= blockCap,
                "burst callbacks must share one bounded budget, got " + writes.size()
                        + " writes for a cap of " + blockCap);
        assertTrue(engine.isRestoring(), "unwritten remainder must still be queued for a later tick");
    }
}
