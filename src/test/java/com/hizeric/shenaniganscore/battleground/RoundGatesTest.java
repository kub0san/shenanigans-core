package com.hizeric.shenaniganscore.battleground;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * The hub/deploy edge cases: no mid-fight refill or escape, no kit farming on death, and no
 * shop access while a round is live.
 */
class RoundGatesTest {
    @Test
    void deployedPlayerCannotRedeploy() {
        assertEquals(RoundGates.Deploy.ALREADY_DEPLOYED,
                RoundGates.deploy(RoundState.RUNNING, true, true));
        assertEquals(RoundGates.Deploy.READY,
                RoundGates.deploy(RoundState.RUNNING, true, false));
    }
    @Test
    void unreadyArenaBlocksDeploy() {
        assertEquals(RoundGates.Deploy.NO_ARENA,
                RoundGates.deploy(RoundState.RUNNING, false, false));
        assertEquals(RoundGates.Deploy.RESTORING,
                RoundGates.deploy(RoundState.RESTORING, true, false));
    }
    @Test
    void onlyIdleDeployStartsARoundThroughTheSharedInvariants() {
        // B1: a deploy from IDLE must route through beginRound() so the diff ledger is opened.
        assertTrue(RoundGates.roundStartRequired(RoundState.IDLE));
        assertFalse(RoundGates.roundStartRequired(RoundState.RUNNING));
        assertFalse(RoundGates.roundStartRequired(RoundState.WARNING));
    }
    @Test
    void mapSwitchIsRefusedDuringRestore() {
        // B2: a tier-1 restore is bound to its starting map; switching mid-restore strands the
        // new map unrestored.
        assertFalse(RoundGates.mapSwitchAllowed(RoundState.STAGING));
        assertFalse(RoundGates.mapSwitchAllowed(RoundState.RESTORING));
        assertTrue(RoundGates.mapSwitchAllowed(RoundState.IDLE));
        assertTrue(RoundGates.mapSwitchAllowed(RoundState.RUNNING));
        assertTrue(RoundGates.mapSwitchAllowed(RoundState.WARNING));
    }
    @Test
    void deployGateReadsTheRoundStateDirectly() {
        // Ticket 07: the manager now feeds the live RoundState into the deploy gate, so a
        // restoring round blocks deploy without a separate boolean.
        // Issue #7: these are the full-restore gate's state contracts. Deploy stays closed
        // through tier 2: RESTORING and STAGING (tier-1 status only, full restore still running)
        // both map to RESTORING below. RUNNING/WARNING and IDLE are READY; RESTORING/STAGING are not.
        assertEquals(RoundGates.Deploy.RESTORING,
                RoundGates.deploy(RoundState.RESTORING, true, false));
        // STAGING: tier-1 gate is open, deployment closed until RUNNING.
        assertEquals(RoundGates.Deploy.RESTORING,
                RoundGates.deploy(RoundState.STAGING, true, false));
        assertEquals(RoundGates.Deploy.READY,
                RoundGates.deploy(RoundState.RUNNING, true, false));
        assertEquals(RoundGates.Deploy.READY,
                RoundGates.deploy(RoundState.WARNING, true, false));
        assertEquals(RoundGates.Deploy.READY,
                RoundGates.deploy(RoundState.IDLE, true, false));
        assertEquals(RoundGates.Deploy.NO_ARENA,
                RoundGates.deploy(RoundState.RUNNING, false, false));
        // Already-deployed wins over an unready arena.
        assertEquals(RoundGates.Deploy.ALREADY_DEPLOYED,
                RoundGates.deploy(RoundState.RESTORING, true, true));
    }
    @Test
    void deathDropsAreClearedOnlyForArenaPlayers() {
        assertEquals(RoundGates.Drops.CLEAR, RoundGates.deathDrops(true, false));
        assertEquals(RoundGates.Drops.CLEAR, RoundGates.deathDrops(false, true));
        assertEquals(RoundGates.Drops.KEEP, RoundGates.deathDrops(false, false));
    }
    @Test
    void hubIsUnavailableWhileDeployed() {
        assertFalse(RoundGates.hubAvailable(true));
        assertTrue(RoundGates.hubAvailable(false));
    }
    @Test
    void captureRefusesWhilePlayersAreInTheArena() {
        assertFalse(RoundGates.captureAllowed(true));
        assertTrue(RoundGates.captureAllowed(false));
    }
    @Test
    void failedRestoreIsNotAnnouncedAsReady() {
        assertEquals(RoundGates.RestoreNotice.FAILED, RoundGates.restoreNotice(true));
        assertEquals(RoundGates.RestoreNotice.READY, RoundGates.restoreNotice(false));
    }
    @Test
    void joinStagesOnlyWhenTheArenaExistsAndThePlayerIsFree() {
        assertEquals(RoundGates.Stage.STAGE_READY, RoundGates.stage(true, false, false));
        assertEquals(RoundGates.Stage.NO_ARENA, RoundGates.stage(false, false, false));
        assertEquals(RoundGates.Stage.ALREADY_DEPLOYED, RoundGates.stage(true, true, false));
        assertEquals(RoundGates.Stage.ALREADY_STAGED, RoundGates.stage(true, false, true));
    }
    @Test
    void deployedWinsOverStagedWhenBothFlagsLeak() {
        assertEquals(RoundGates.Stage.ALREADY_DEPLOYED, RoundGates.stage(true, true, true));
    }
    @Test
    void roundEndReturnsEveryDeployedPlayerToTheHub() {
        // Round end (and only round end) pulls everyone — including mid-fight and mid-dive —
        // back to the hub so regeneration can run on an empty arena.
        assertEquals(RoundGates.RoundEnd.HUB, RoundGates.roundEnd(true, false, false));
        // A player already staged in the hub stays in the hub.
        assertEquals(RoundGates.RoundEnd.HUB, RoundGates.roundEnd(false, true, false));
        // A mid-dive player is still deployed; round end must claim them too.
        assertEquals(RoundGates.RoundEnd.HUB, RoundGates.roundEnd(true, true, false));
    }
    @Test
    void roundEndIgnoresPlayersOutsideTheMode() {
        // Someone never in the mode is not pulled anywhere by a round transition.
        assertEquals(RoundGates.RoundEnd.NONE, RoundGates.roundEnd(false, false, false));
    }

    @Test
    void secondTierOneGateForTheSameResetDoesNotStartAnotherRound() {
        // RoundController.onTierOneGateOpen() delegates its guard to this one-shot gate: the
        // restore engine can fire tier 1 synchronously (empty diff) and again on completion.
        // The only production caller is the guard at RoundController.java:299; it needs a live
        // BattlegroundManager/Bukkit to reach beginRound() and the diff ledger, so that wiring is
        // covered by source, not by a mock harness here. This pins the guard's observable contract.
        RoundController.TierOneGate gate = new RoundController.TierOneGate();
        gate.reset(); // beginReset()
        assertFalse(gate.consumed(), "a fresh reset arms the gate");

        assertTrue(gate.tryOpen(), "the first tier-1 gate must open the round");
        assertTrue(gate.consumed(), "the round is started and the ledger opened exactly once");
        assertFalse(gate.tryOpen(), "a duplicate gate for the same reset must not advance the round");
        assertTrue(gate.consumed(), "a spent gate stays spent: no second round and no reopened ledger");

        gate.reset(); // the next reset arms a fresh gate
        assertFalse(gate.consumed());
        assertTrue(gate.tryOpen(), "a new round gets a fresh one-shot gate");
    }
}
