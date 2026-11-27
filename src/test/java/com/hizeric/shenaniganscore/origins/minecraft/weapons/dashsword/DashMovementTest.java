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
}
