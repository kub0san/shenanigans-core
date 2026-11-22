package com.hizeric.shenaniganscore.battleground.admin;

import com.hizeric.shenaniganscore.battleground.RoundState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ticket 12: admin round-control decisions at the pure admin-config seam. No Bukkit, no GUI —
 * the GUI asks this gate whether an action is allowed and what the manager should execute.
 */
class AdminRoundGateTest {

    @Test
    void startIsDeniedWithoutACapturedArena() {
        AdminRoundGate.Result result = AdminRoundGate.decide(
                AdminRoundGate.Action.START, RoundState.IDLE, false, 0);

        assertFalse(result.allowed());
    }

    @Test
    void startIsAllowedWithACapturedArenaAndResetsTheClock() {
        AdminRoundGate.Result result = AdminRoundGate.decide(
                AdminRoundGate.Action.START, RoundState.IDLE, true, 0);

        assertTrue(result.allowed());
        assertEquals(AdminRoundGate.Effect.START, result.effect());
        assertEquals(1200, result.seconds());
    }

    @Test
    void startIsDeniedWhileTheArenaIsStillResetting() {
        assertFalse(AdminRoundGate.decide(AdminRoundGate.Action.START,
                RoundState.RESTORING, true, 0).allowed());
        assertFalse(AdminRoundGate.decide(AdminRoundGate.Action.START,
                RoundState.STAGING, true, 0).allowed());
    }

    @Test
    void forceStartRefusesWhileARestoreIsStillInFlight() {
        assertTrue(AdminRoundGate.mayForceStart(RoundState.IDLE, true, false));
        assertFalse(AdminRoundGate.mayForceStart(RoundState.IDLE, true, true));
        assertFalse(AdminRoundGate.mayForceStart(RoundState.RESTORING, true, false));
        assertFalse(AdminRoundGate.mayForceStart(RoundState.STAGING, true, false));
        assertFalse(AdminRoundGate.mayForceStart(RoundState.IDLE, false, false));
    }

    @Test
    void stopRequiresALiveRound() {
        AdminRoundGate.Result idle = AdminRoundGate.decide(
                AdminRoundGate.Action.STOP, RoundState.IDLE, true, 0);
        assertFalse(idle.allowed());

        AdminRoundGate.Result running = AdminRoundGate.decide(
                AdminRoundGate.Action.STOP, RoundState.RUNNING, true, 0);
        assertTrue(running.allowed());
        assertEquals(AdminRoundGate.Effect.STOP, running.effect());
    }

    @Test
    void endRequiresALiveRoundAndTriggersTheResetPath() {
        AdminRoundGate.Result idle = AdminRoundGate.decide(
                AdminRoundGate.Action.END, RoundState.IDLE, true, 0);
        assertFalse(idle.allowed());

        AdminRoundGate.Result running = AdminRoundGate.decide(
                AdminRoundGate.Action.END, RoundState.RUNNING, true, 0);
        assertTrue(running.allowed());
        assertEquals(AdminRoundGate.Effect.END, running.effect());

        AdminRoundGate.Result warning = AdminRoundGate.decide(
                AdminRoundGate.Action.END, RoundState.WARNING, true, 0);
        assertTrue(warning.allowed());
    }

    @Test
    void skipJumpsTheClockToZeroSoTheNormalEndPathRuns() {
        AdminRoundGate.Result staging = AdminRoundGate.decide(
                AdminRoundGate.Action.SKIP, RoundState.STAGING, true, 0);
        assertFalse(staging.allowed());

        AdminRoundGate.Result running = AdminRoundGate.decide(
                AdminRoundGate.Action.SKIP, RoundState.RUNNING, true, 0);
        assertTrue(running.allowed());
        assertEquals(AdminRoundGate.Effect.SET_TIME, running.effect());
        assertEquals(0, running.seconds());
    }

    @Test
    void setTimeIsAllowedDuringALiveRoundAndPassesTheRequestThrough() {
        AdminRoundGate.Result staging = AdminRoundGate.decide(
                AdminRoundGate.Action.SET_TIME, RoundState.STAGING, true, 90);
        assertFalse(staging.allowed());

        AdminRoundGate.Result warning = AdminRoundGate.decide(
                AdminRoundGate.Action.SET_TIME, RoundState.WARNING, true, 90);
        assertTrue(warning.allowed());
        assertEquals(AdminRoundGate.Effect.SET_TIME, warning.effect());
        assertEquals(90, warning.seconds()); // RoundClock owns the clamp
    }
}
