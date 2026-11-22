package com.hizeric.shenaniganscore.battleground.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The `/back`-style entry exploit. These are the exact branches `ArenaEntryGuard`
 * applies to a teleport whose destination lands inside the arena.
 */
class ArenaEntryDecisionTest {

    private static final ArenaEntryDecision.Input INSIDE_NOBODY = new ArenaEntryDecision.Input(
            true, false, false, false, false);

    @Test
    void outsideTheArenaIsAlwaysAllowed() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(false, false, false, false, false)));
    }

    @Test
    void aStrangerIsTurnedBack() {
        assertEquals(ArenaEntryDecision.Verdict.DENY, ArenaEntryDecision.evaluate(INSIDE_NOBODY));
    }

    @Test
    void aDeployedPlayerIsComingBack() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(true, true, false, false, false)));
    }

    @Test
    void aStagedPlayerIsWaitingForTheRound() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(true, false, true, false, false)));
    }

    @Test
    void anAdminCanEnter() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(true, false, false, true, false)));
    }

    @Test
    void thePluginsOwnTeleportIsAuthorised() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(true, false, false, false, true)));
    }

    @Test
    void aTokenDoesNotOverrideTheOutsideRule() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(false, false, false, false, true)));
    }

    @Test
    void nullInputIsAllowedSoTheGuardNeverBreaks() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW, ArenaEntryDecision.evaluate(null));
    }

    @Test
    void sameBlockAcceptsAnIdenticalTriple() {
        assertTrue(ArenaEntryDecision.sameBlock(10, 64, -3, 10, 64, -3));
    }

    @Test
    void sameBlockRejectsAYOnlyDifference() {
        assertFalse(ArenaEntryDecision.sameBlock(10, 64, -3, 10, 65, -3));
    }

    @Test
    void sameBlockRejectsAnXOnlyDifference() {
        assertFalse(ArenaEntryDecision.sameBlock(10, 64, -3, 11, 64, -3));
    }

    @Test
    void sameBlockRejectsAZOnlyDifference() {
        assertFalse(ArenaEntryDecision.sameBlock(10, 64, -3, 10, 64, -2));
    }
}
