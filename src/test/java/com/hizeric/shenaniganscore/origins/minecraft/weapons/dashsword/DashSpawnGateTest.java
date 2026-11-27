package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure spawn gate for the Rift Cleave dash — no server required. */
class DashSpawnGateTest {

    @Test
    void blockedDownwardGroundDashSpawnsNothing() {
        assertFalse(DashSwordEffect.shouldSpawnAt(-0.5, true, false, 2.0, 1.0));
        assertTrue(DashSwordEffect.shouldSpawnAt(0.0, true, false, 2.0, 1.0));
    }

    @Test
    void firstTickMotionlessStillSpawnsLaunchCue() {
        // Horizontal, airborne, motionless first tick: the seam must still spawn the launch cue.
        assertTrue(DashSwordEffect.shouldSpawnAt(0.0, false, false, 0.0, 1.0));
    }
}