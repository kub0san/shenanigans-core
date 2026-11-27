package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MannequinEchoCapabilityTest {
    @Test
    void firstFailureDisablesOnce() {
        EchoCapability c = new EchoCapability();
        assertTrue(c.trip(new RuntimeException("bad metadata")));
        assertFalse(c.trip(new RuntimeException("another")));
        assertTrue(c.disabled());
    }

    @Test
    void resetReenables() {
        EchoCapability c = new EchoCapability();
        c.trip(new RuntimeException());
        c.reset();
        assertFalse(c.disabled());
    }
}