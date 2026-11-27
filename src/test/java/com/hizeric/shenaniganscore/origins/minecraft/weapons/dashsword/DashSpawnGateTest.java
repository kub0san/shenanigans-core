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
    void genuineStallSpawnsNothing() {
        double expectedStep = DashMovement.speed(2.4, 0.035, 28);
        assertFalse(DashSwordEffect.shouldSpawnAt(0.0, false, true, 0.0, expectedStep));
        assertTrue(DashSwordEffect.shouldSpawnAt(0.0, false, true, 0.5, expectedStep));
    }
}