package com.hizeric.shenaniganscore.battleground.deploy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
