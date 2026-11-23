package com.hizeric.shenaniganscore.battleground;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
/**
 * Issue #7: "ready" must mean the FULL restore finished, not just tier 1. Pins the pure
 * readiness decision and its copy before RoundController/DeployService delegate to it.
 */
class RoundReadinessTest {
    @Test
    void tierOneGateDoesNotOpenDeploy() {
        RoundReadiness readiness = new RoundReadiness();
        assertEquals(RoundReadiness.Outcome.ANNOUNCE_PENDING, readiness.onTierOneDone());
        assertFalse(readiness.opened(), "tier 1 alone must not open deploy");
        assertEquals(RoundReadiness.Outcome.NONE, readiness.onTierOneDone());
        assertFalse(readiness.opened(), "a duplicate tier-1 gate still must not open deploy");
    }
    @Test
    void fullRestoreOpensRoundWithReadyAnnouncement() {
        RoundReadiness readiness = new RoundReadiness();
        readiness.onTierOneDone();
        assertEquals(RoundReadiness.Outcome.OPEN_CLEAN, readiness.onRestoreFinished(false));
        assertTrue(readiness.opened(), "only the full restore opens deploy");
        assertTrue(readiness.tierOneSeen(),
                "the full restore also consumes the tier-1 one-shot so a late gate cannot re-announce");
        assertEquals("<green>Round <white>4<green> is ready — deploy with /battleground join",
                RoundReadiness.announcement(4, false));
    }
    @Test
    void failedRestoreOpensWithDirtyWarningAndNoReadyClaim() {
        RoundReadiness readiness = new RoundReadiness();
        assertEquals(RoundReadiness.Outcome.OPEN_DIRTY, readiness.onRestoreFinished(true));
        String announcement = RoundReadiness.announcement(4, true);
        assertFalse(announcement.contains("is ready"), "a failed restore must not claim ready");
        assertTrue(announcement.contains("Restore failed — the arena may be dirty."));
        assertTrue(announcement.contains("Restoration will be retried next round."));
    }
    @Test
    void aLateCompletionCannotReopenTheRoundWithDeployedPlayers() {
        RoundReadiness readiness = new RoundReadiness();
        assertEquals(RoundReadiness.Outcome.OPEN_CLEAN, readiness.onRestoreFinished(false));
        assertEquals(RoundReadiness.Outcome.NONE, readiness.onRestoreFinished(false));
        assertTrue(readiness.opened(), "a late completion must not reopen the round");
    }
    @Test
    void resetArmsAFreshReadinessGeneration() {
        RoundReadiness readiness = new RoundReadiness();
        assertEquals(RoundReadiness.Outcome.OPEN_CLEAN, readiness.onRestoreFinished(false));
        readiness.reset();
        assertFalse(readiness.tierOneSeen(), "reset clears the tier-1 one-shot");
        assertFalse(readiness.opened(), "reset clears the full-restore one-shot");
        assertEquals(RoundReadiness.Outcome.ANNOUNCE_PENDING, readiness.onTierOneDone());
    }
    @Test
    void statusAndWaitingMessagesNameTheFullRestoreGate() {
        String status = RoundReadiness.statusAnnouncement();
        assertTrue(status.contains("verifying the arena"));
        assertFalse(status.contains("ready"), "no message says ready before completion");
        assertTrue(RoundReadiness.waitingMessage(RoundState.STAGING).contains("tier 1"));
        String restoring = RoundReadiness.waitingMessage(RoundState.RESTORING);
        assertTrue(restoring.contains("restoring"));
        assertFalse(restoring.contains("ready"), "no message says ready before completion");
    }
}
