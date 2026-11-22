package com.hizeric.shenaniganscore.battleground.deploy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The mid-dive contact decision, kept pure so the explicit liquid handling can be verified without
 * a live world. Liquid contact is reported ahead of ground contact and must therefore be routed to
 * the re-stage handler rather than treated as a safe touchdown.
 */
class DiveContactTest {

    @Test
    void liquidAtTheFeetIsReportedEvenWhenOnGround() {
        assertEquals(DiveContact.Kind.LIQUID, DiveContact.classify(true, true, false));
        assertEquals(DiveContact.Kind.LIQUID, DiveContact.classify(false, true, false));
    }

    @Test
    void liquidAtTheBodyIsReported() {
        assertEquals(DiveContact.Kind.LIQUID, DiveContact.classify(true, false, true));
    }

    @Test
    void solidGroundWithoutLiquidIsANormalTouchdown() {
        assertEquals(DiveContact.Kind.GROUND, DiveContact.classify(true, false, false));
    }

    @Test
    void openAirIsNoContact() {
        assertEquals(DiveContact.Kind.NONE, DiveContact.classify(false, false, false));
    }
}
