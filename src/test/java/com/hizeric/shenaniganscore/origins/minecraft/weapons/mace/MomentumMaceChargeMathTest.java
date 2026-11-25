package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Pure recharge math — no Bukkit server needed. */
class MomentumMaceChargeMathTest {
    private static final long INTERVAL = 10_000L;
    private static final int MAX = 3;

    @Test
    void fullChargesHaveNoTimer() {
        var r = MomentumMaceChargeManager.calculate(3, 999L, 10_000L, INTERVAL, MAX);
        assertEquals(3, r.charges());
        assertEquals(0L, r.nextRechargeMillis());
    }

    @Test
    void sequentialRecharge() {
        long start = 0L;
        var r = MomentumMaceChargeManager.calculate(0, start + INTERVAL, start + INTERVAL, INTERVAL, MAX);
        assertEquals(1, r.charges());
        assertEquals(start + 2 * INTERVAL, r.nextRechargeMillis());
    }

    @Test
    void multipleElapsedIntervalsCatchUpAndClamp() {
        var r = MomentumMaceChargeManager.calculate(0, INTERVAL, INTERVAL * 10, INTERVAL, MAX);
        assertEquals(MAX, r.charges());
        assertEquals(0L, r.nextRechargeMillis());
    }

    @Test
    void hugeElapsedTimeDoesNotOverflow() {
        var r = MomentumMaceChargeManager.calculate(1, INTERVAL, Long.MAX_VALUE / 2, INTERVAL, MAX);
        assertEquals(MAX, r.charges());
        assertEquals(0L, r.nextRechargeMillis());
    }

    @Test
    void corruptedStateIsClamped() {
        var negative = MomentumMaceChargeManager.calculate(-5, 0L, 0L, INTERVAL, MAX);
        assertEquals(0, negative.charges());
        var over = MomentumMaceChargeManager.calculate(99, 0L, 0L, INTERVAL, MAX);
        assertEquals(MAX, over.charges());
    }

    @Test
    void timerDoesNotResetOnEarlySpend() {
        var r = MomentumMaceChargeManager.calculate(2, 10_000L, 8_000L, INTERVAL, MAX);
        assertEquals(2, r.charges());
        assertEquals(10_000L, r.nextRechargeMillis());
    }
}
