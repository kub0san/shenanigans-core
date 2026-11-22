package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.Policy;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.TicketLedger;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.WorkBudget;
import com.hizeric.shenaniganscore.battleground.arena.restore.ArenaChunkWork.Write;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure restore-policy and budget seams: no server, no Bukkit, no files. */
class ArenaChunkWorkTest {

    @Test
    void ticketLedgerAcquiresOnceAndReleasesIdempotently() {
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        TicketLedger<String> ledger = new TicketLedger<>(added::add, removed::add);

        ledger.acquire("world/0/0");
        ledger.acquire("world/0/0");
        assertEquals(List.of("world/0/0"), added);
        assertEquals(1, ledger.heldCount());

        ledger.release("world/0/0");
        ledger.release("world/0/0");
        assertEquals(List.of("world/0/0"), removed);
        assertEquals(0, ledger.heldCount());

        ledger.acquire("world/1/1");
        ledger.acquire("world/2/2");
        ledger.releaseAll();
        assertEquals(List.of("world/0/0", "world/1/1", "world/2/2"), removed);
        assertEquals(0, ledger.heldCount());
        ledger.releaseAll();
        assertEquals(3, removed.size(), "releaseAll is idempotent");
    }

    @Test
    void workBudgetHonoursMillisecondGuardWithInjectedClock() {
        long[] now = {0L};
        WorkBudget budget = new WorkBudget(30, 256, 4, () -> now[0]);
        budget.beginTick();
        assertTrue(budget.canContinue());
        now[0] = 29_999_999L;
        assertTrue(budget.canContinue());
        now[0] = 30_000_000L;
        assertFalse(budget.canContinue());
    }

    @Test
    void workBudgetRespectsBlockAndChunkCapsAndResetsEachTick() {
        long[] now = {0L};
        WorkBudget budget = new WorkBudget(30, 3, 2, () -> now[0]);
        budget.beginTick();
        assertTrue(budget.canWriteBlock());
        budget.recordBlock();
        budget.recordBlock();
        budget.recordBlock();
        assertFalse(budget.canWriteBlock());
        assertTrue(budget.canStartChunk());
        budget.recordChunk();
        budget.recordChunk();
        assertFalse(budget.canStartChunk());

        budget.beginTick();
        assertTrue(budget.canWriteBlock(), "caps are per tick");
        assertTrue(budget.canStartChunk());
    }

    @Test
    void policyDerivesSectionCapFromLiveConfigChunkCap() {
        Policy policy = Policy.from(ArenaRuntimeConfig.defaults());
        assertEquals(30, policy.msPerTick());
        assertEquals(256, policy.blocksPerTick());
        assertEquals(4, policy.chunksPerTick());
        assertEquals(8, policy.sectionsPerTick(), "two sections per chunk, same as the ledger ruling");
        assertTrue(policy.relight());
        assertTrue(policy.verify());
        assertTrue(policy.fallback());
        assertTrue(policy.deferNearPlayer());
    }

    @Test
    void relightTargetsOnlyFluidAndLightStatesAndHonoursToggle() {
        assertTrue(ArenaChunkWork.needsRelight("minecraft:water[level=1]"));
        assertTrue(ArenaChunkWork.needsRelight("minecraft:lava"));
        assertTrue(ArenaChunkWork.needsRelight("minecraft:glowstone"));
        assertTrue(ArenaChunkWork.needsRelight("minecraft:sea_lantern"));
        assertTrue(ArenaChunkWork.needsRelight("minecraft:fire"));
        assertFalse(ArenaChunkWork.needsRelight("minecraft:stone"));
        assertFalse(ArenaChunkWork.needsRelight("minecraft:air"));
        assertFalse(ArenaChunkWork.needsRelight(null));

        Set<Long> positions = Set.of(1L, 2L, 3L);
        Map<Long, String> states = Map.of(1L, "minecraft:water", 2L, "minecraft:stone",
                3L, "minecraft:glowstone");
        assertEquals(Set.of(1L, 3L), ArenaChunkWork.relightTargets(positions, states::get, true));
        assertTrue(ArenaChunkWork.relightTargets(positions, states::get, false).isEmpty(),
                "arena.restore.relight=false disables the whole pass");
    }

    @Test
    void bulkWritesUseNoPhysicsWhileRelightReappliesFluidsWithPhysics() {
        Write bulk = Write.bulk(BlockKey.pack(1, 61, 1), "minecraft:stone");
        Write relight = Write.relight(BlockKey.pack(1, 61, 1), "minecraft:water[level=1]");
        assertFalse(bulk.applyPhysics(), "bulk tier writes never tick physics");
        assertTrue(relight.applyPhysics(), "water/lava are re-applied with physics=true");
        assertEquals("minecraft:stone", bulk.state());
        assertEquals("minecraft:water[level=1]", relight.state());
        assertEquals(BlockKey.pack(1, 61, 1), bulk.packed());
    }

    @Test
    void nearPlayerDefersPlayersInsideSmallRadius() {
        List<int[]> players = List.of(new int[]{10, 10});
        assertTrue(ArenaChunkWork.nearPlayer(12, 10, players, 4.0));
        assertTrue(ArenaChunkWork.nearPlayer(12, 10, players, 2.0), "boundary is inclusive");
        assertFalse(ArenaChunkWork.nearPlayer(12, 10, players, 1.9));
        assertFalse(ArenaChunkWork.nearPlayer(20, 10, players, 4.0));
        assertFalse(ArenaChunkWork.nearPlayer(12, 10, List.of(), 4.0));
    }

    @Test
    void packedPositionsRoundTripIncludingNegativeCoordinatesAndLimits() {
        int[][] coords = {{0, 0, 0}, {1, 61, 1}, {-5, -3, -9},
                {33_554_431, 2047, 33_554_431}, {-33_554_432, -2048, -33_554_432}};
        for (int[] c : coords) {
            long packed = BlockKey.pack(c[0], c[1], c[2]);
            assertEquals(c[0], BlockKey.unpackX(packed), "x round trip");
            assertEquals(c[1], BlockKey.unpackY(packed), "y round trip");
            assertEquals(c[2], BlockKey.unpackZ(packed), "z round trip");
        }
    }
}
