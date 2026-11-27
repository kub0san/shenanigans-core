package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashMovementTest {
    @Test
    void steerWithZeroAlphaKeepsDirection() {
        Vector d = new Vector(0, 0, 1);
        assertEquals(d, DashMovement.steer(d, new Vector(1, 0, 0), 0.0));
    }

    @Test
    void steerWithFullAlphaAdoptsLook() {
        Vector res = DashMovement.steer(new Vector(0, 0, 1), new Vector(1, 0, 0), 1.0);
        assertEquals(1.0, res.getX(), 1e-9);
        assertEquals(0.0, res.getZ(), 1e-9);
    }

    @Test
    void steerClampsAlphaAndHandlesZeroVectors() {
        Vector res = DashMovement.steer(new Vector(0, 0, 0), new Vector(0, 0, 0), 5.0);
        assertTrue(res.lengthSquared() > 0);
    }

    @Test
    void speedWithZeroDecayIsConstant() {
        assertEquals(2.6, DashMovement.speed(2.6, 0.0, 10), 1e-9);
    }

    @Test
    void speedDecaysOverTicks() {
        assertTrue(DashMovement.speed(2.6, 0.1, 5) < 2.6);
        assertEquals(0.0, DashMovement.speed(2.6, 1.0, 1), 1e-9);
    }

    @Test
    void velocityScalesDirection() {
        Vector v = DashMovement.velocity(new Vector(0, 0, 1), 2.6);
        assertEquals(0.0, v.getX(), 1e-9);
        assertEquals(2.6, v.getZ(), 1e-9);
    }

    @Test
    void headingAtTurnsRightWithoutChangingPitch() {
        Vector h = DashMovement.headingAt(new Vector(0, 0, 1), 28);
        assertEquals(44.8, Math.toDegrees(Math.atan2(h.getX(), h.getZ())), 0.1);
        assertEquals(0.0, h.getY(), 1e-9);
    }

    @Test
    void headingAtHandlesVerticalInput() {
        assertEquals(new Vector(1, 0, 0), DashMovement.headingAt(new Vector(0, 1, 0), 0));
    }

    @Test
    void headingAtStaysNormalized() {
        Vector launch = new Vector(0.3, 0.9, -0.4);
        Vector h = DashMovement.headingAt(launch, 13);
        assertEquals(1.0, h.length(), 1e-9);
        assertEquals(launch.clone().normalize().getY(), h.getY(), 1e-9);
    }

    @Test
    void headingAtPreservesPitchAndDriftsYaw() {
        Vector launch = new Vector(0, -1, 1);
        double expectedY = -1.0 / Math.sqrt(2.0);

        Vector start = DashMovement.headingAt(launch, 0);
        assertEquals(expectedY, start.getY(), 1e-9);
        assertEquals(0.0, start.getX(), 1e-9);
        assertEquals(1.0 / Math.sqrt(2.0), start.getZ(), 1e-9);

        Vector later = DashMovement.headingAt(launch, 28);
        assertEquals(expectedY, later.getY(), 1e-9);
        assertEquals(44.8, Math.toDegrees(Math.atan2(later.getX(), later.getZ())), 0.1);
        assertEquals(1.0, later.length(), 1e-9);
    }

    @Test
    void distanceAfterUsesDecayedSteps() {
        assertEquals(DashMovement.distanceAfter(28, 2.4, 0.035),
                DashMovement.distanceAfter(27, 2.4, 0.035)
                        + DashMovement.speed(2.4, 0.035, 28), 1e-9);
    }

    @Test
    void distanceAfterIsZeroWithoutTicks() {
        assertEquals(0.0, DashMovement.distanceAfter(0, 2.4, 0.035), 1e-9);
        assertEquals(0.0, DashMovement.distanceAfter(-5, 2.4, 0.035), 1e-9);
    }
}
