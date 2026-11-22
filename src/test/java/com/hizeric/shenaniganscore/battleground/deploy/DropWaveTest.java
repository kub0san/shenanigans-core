package com.hizeric.shenaniganscore.battleground.deploy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure fall-distance arithmetic used to synchronize a burst of deploys. */
class DropWaveTest {

    @Test
    void fallDistanceFollowsTheDiveRamp() {
        assertEquals(0.5 + DiveMotion.DOWN_SPEED_RAMP,
                DropWave.fallDistance(1, 2.0), 1e-9);
        assertTrue(DropWave.fallDistance(40, 2.0) > DropWave.fallDistance(20, 2.0));
    }

    @Test
    void fallDistanceCapsAtMaxSpeed() {
        double one = DropWave.fallDistance(1, 0.6);
        double two = DropWave.fallDistance(2, 0.6);
        assertEquals(0.6, two - one, 1e-9);
    }

    @Test
    void releaseYSolvesFromTheTargetFallTimeAndRespectsTheRoof() {
        int release = DropWave.releaseY(30, 40, 2.0, 256);
        int expected = 30 + (int) Math.ceil(DropWave.fallDistance(40, 2.0));

        assertEquals(expected, release);
        assertTrue(release > 30);
        assertEquals(Math.min(154, expected), DropWave.releaseY(30, 40, 2.0, 156));
        assertTrue(DropWave.releaseY(30, 40, 2.0, 60) <= 58);
    }

    @Test
    void zeroTicksStillUsesTheThreeBlockMinimum() {
        assertEquals(33, DropWave.releaseY(30, 0, 2.0, 256));
    }
}
