package com.hizeric.shenaniganscore.battleground.ability;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Command-only casting with a live streak gate: no sneak, no hotbar, no keybind. */
class CastInputTest {

    @Test
    void ignoresSlotsOutsideTheThreeCommandSlots() {
        assertEquals(CastInput.Decision.IGNORE, CastInput.decide(true, true, false, true, 3));
        assertEquals(CastInput.Decision.IGNORE, CastInput.decide(true, true, false, true, -1));
    }

    @Test
    void castsOnlyWhenDeployedAndOwned() {
        assertEquals(CastInput.Decision.CAST, CastInput.decide(true, true, false, true, 2));
        assertEquals(CastInput.Decision.IGNORE, CastInput.decide(false, true, false, true, 0));
        assertEquals(CastInput.Decision.IGNORE, CastInput.decide(true, false, false, true, 0));
    }

    @Test
    void cooldownOrUnmetStreakReportsNotReady() {
        assertEquals(CastInput.Decision.NOT_READY, CastInput.decide(true, true, true, true, 1));
        assertEquals(CastInput.Decision.NOT_READY, CastInput.decide(true, true, false, false, 1));
    }

    @Test
    void typedResultReportsTheReasonAndCooldown() {
        CastInput.Result result = CastInput.decide(true, true, true, true, 2, true, true, 7);

        assertEquals(CastInput.Decision.NOT_READY, result.decision());
        assertEquals(CastInput.Reason.ON_COOLDOWN, result.reason());
        assertEquals(7, result.cooldownSeconds());
    }

    @Test
    void eachGateFailureHasItsOwnReason() {
        assertEquals(CastInput.Reason.BAD_SLOT,
                CastInput.decide(true, true, false, true, 3, true, true, 0).reason());
        assertEquals(CastInput.Reason.NOT_DEPLOYED,
                CastInput.decide(false, true, false, true, 0, true, true, 0).reason());
        assertEquals(CastInput.Reason.NOT_OWNED,
                CastInput.decide(true, false, false, true, 0, true, true, 0).reason());
        assertEquals(CastInput.Reason.HELD_ITEM_MISMATCH,
                CastInput.decide(true, true, false, true, 0, false, true, 0).reason());
        assertEquals(CastInput.Reason.ULTIMATE_NOT_BOUND,
                CastInput.decide(true, true, false, true, 2, true, false, 0).reason());
        assertEquals(CastInput.Reason.STREAK_NOT_MET,
                CastInput.decide(true, true, false, false, 1, true, true, 0).reason());
        assertEquals(CastInput.Reason.OK,
                CastInput.decide(true, true, false, true, 1, true, true, 0).reason());
    }
}
