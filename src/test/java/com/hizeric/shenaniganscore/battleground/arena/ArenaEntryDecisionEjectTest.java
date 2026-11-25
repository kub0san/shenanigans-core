package com.hizeric.shenaniganscore.battleground.arena;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Issue #64: the pure arena-entry decision must separate an ingress attempt (an unauthorised
 * player whose destination is inside) from an unauthorised player who is already inside and is
 * trying to walk out. The latter is EJECT so the listener can send them to the return point
 * instead of rewarding the exploit by letting them leave.
 */
class ArenaEntryDecisionEjectTest {

    private static ArenaEntryDecision.Input input(boolean destination, boolean origin,
                                                  boolean deployed, boolean staged,
                                                  boolean admin, boolean internal) {
        return new ArenaEntryDecision.Input(destination, origin, deployed, staged, admin, internal);
    }

    @Test
    void ingressByAnUnauthorisedStrangerIsStillDenied() {
        assertEquals(ArenaEntryDecision.Verdict.DENY,
                ArenaEntryDecision.evaluate(input(true, false, false, false, false, false)));
    }

    @Test
    void anUnauthorisedPlayerWhoStartsInsideIsEjected() {
        assertEquals(ArenaEntryDecision.Verdict.EJECT,
                ArenaEntryDecision.evaluate(input(false, true, false, false, false, false)));
    }

    @Test
    void anUnauthorisedMoveBetweenTwoInsideBlocksIsAlsoEjected() {
        assertEquals(ArenaEntryDecision.Verdict.EJECT,
                ArenaEntryDecision.evaluate(input(true, true, false, false, false, false)));
    }

    @Test
    void aDeployedExitStaysAllowedSoDeployServiceOwnsTheExit() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(input(false, true, true, false, false, false)));
    }

    @Test
    void aStagedOriginInsideStaysAllowed() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(input(false, true, false, true, false, false)));
    }

    @Test
    void anAdminOriginInsideStaysAllowed() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(input(false, true, false, false, true, false)));
    }

    @Test
    void anInternalTransitOriginInsideStaysAllowed() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(input(false, true, false, false, false, true)));
    }

    @Test
    void anInternalTokenStillAuthorisesIngress() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(input(true, false, false, false, false, true)));
    }

    @Test
    void noOriginAndNoDestinationFailOpen() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW,
                ArenaEntryDecision.evaluate(input(false, false, false, false, false, false)));
    }

    @Test
    void nullInputFailsOpen() {
        assertEquals(ArenaEntryDecision.Verdict.ALLOW, ArenaEntryDecision.evaluate(null));
    }
}
