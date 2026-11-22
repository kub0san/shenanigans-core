package com.hizeric.shenaniganscore.battleground.deploy;

import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.scheduler.BukkitSchedulerMock;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pure dive-kinematics seam. The airdrop is now a strictly vertical descent: the per-tick
 * velocity has zero horizontal components, so the player never drifts off the scored landing
 * column. Verified as arithmetic so the feel is pinned without a live world.
 */
class DiveMotionTest {

    private static final double DOWN_CAP = 2.0;

    @Test
    void downSpeedStartsSlowAndRampsUp() {
        double first = DiveMotion.downSpeed(1, DOWN_CAP);
        double later = DiveMotion.downSpeed(8, DOWN_CAP);

        assertEquals(0.62, first, 1e-9, "tick 1 down-speed is 0.5 + 1*0.12");
        assertTrue(later > first, "the fall accelerates over the first ticks");
    }

    @Test
    void downSpeedCapsAtTheConfiguredMaximum() {
        assertEquals(DOWN_CAP, DiveMotion.downSpeed(1000, DOWN_CAP), 1e-9,
                "a long fall never exceeds the down-speed cap");
    }

    @Test
    void theDropIsAVerticalDescentWithNoHorizontalDrift() {
        DiveMotion.Velocity velocity = DiveMotion.forTick(1, DOWN_CAP);

        assertEquals(0.0, velocity.x(), 1e-9, "no forward/sideways drift");
        assertEquals(0.0, velocity.z(), 1e-9, "no forward/sideways drift");
        assertTrue(velocity.y() < 0.0, "the only motion is downward");
        assertEquals(-DiveMotion.downSpeed(1, DOWN_CAP), velocity.y(), 1e-9);
    }

    @Test
    void everyTickOfALongFallRemainsDriftFree() {
        for (int tick = 1; tick <= 200; tick++) {
            DiveMotion.Velocity velocity = DiveMotion.forTick(tick, DOWN_CAP);
            assertEquals(0.0, velocity.x(), 1e-9);
            assertEquals(0.0, velocity.z(), 1e-9);
        }
    }

    @AfterEach
    void unmockBukkit() {
        MockBukkit.unmock();
    }

    private record DiveHarness(ServerMock server, UUID uuid, DiveService dives, SkyDrop skyDrop) { }

    private static DiveHarness startDive() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        DiveService dives = new DiveService();
        SkyDrop skyDrop = new SkyDrop(MockBukkit.createMockPlugin("SkyDropTest"), dives, p -> { }, p -> { });
        var start = player.getLocation();
        skyDrop.begin(player, start, start.getBlockY() - 1);
        return new DiveHarness(server, player.getUniqueId(), dives, skyDrop);
    }

    private static BukkitTask onlyQueuedTask(ServerMock server) {
        List<BukkitTask> pending = ((BukkitSchedulerMock) server.getScheduler()).getPendingTasks();
        assertEquals(1, pending.size(), "one BukkitTask is scheduled per dive");
        return pending.get(0);
    }

    @Test
    void cancelStopsTheDiveTaskAndClearsTheDivingFlag() {
        DiveHarness harness = startDive();
        BukkitTask task = onlyQueuedTask(harness.server());

        harness.skyDrop().cancel(harness.uuid());

        assertFalse(harness.server().getScheduler().isQueued(task.getTaskId()),
                "cancel(UUID) must stop the per-dive task");
        assertFalse(harness.dives().isDiving(harness.uuid()),
                "cancel(UUID) must release the dive lock");
    }

    @Test
    void clearByUuidAlsoStopsTheDiveTask() {
        DiveHarness harness = startDive();
        BukkitTask task = onlyQueuedTask(harness.server());

        harness.dives().clear(harness.uuid());

        assertFalse(harness.server().getScheduler().isQueued(task.getTaskId()),
                "every DiveService.clear(UUID) caller is covered without editing RoundController");
        assertFalse(harness.dives().isDiving(harness.uuid()));
    }

    @Test
    void clearAllStopsEveryDiveTask() {
        DiveHarness harness = startDive();
        BukkitTask task = onlyQueuedTask(harness.server());

        harness.dives().clear();

        assertFalse(harness.server().getScheduler().isQueued(task.getTaskId()),
                "the no-arg clear must stop tasks too (disable / beginReset callers)");
        assertFalse(harness.dives().isDiving(harness.uuid()));
    }
}
