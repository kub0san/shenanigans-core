package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rotation curve owned by a session's camera shake.
 *
 * <p>Cleave used to write a random rotation straight onto the victim with no
 * base write-back, so the victim never returned to the facing they had before
 * the ravage. The fix routes that jitter through the session's existing
 * {@code Shake} restoration path: the shake records the victim's base rotation,
 * wobbles around it with a decaying sine, and lands exactly back on it at
 * expiry. These tests pin that curve — including the exact expiry write-back
 * and the pitch clamp — without needing a live server.</p>
 */
class ShakeRotationTest {

    @Test
    void rotationLandsExactlyOnBaseAtExpiry() {
        Session.Shake shake = new Session.Shake(120f, -10f, 7.0, 1_000L, 200L);
        assertEquals(new Session.Rotation(120f, -10f), shake.rotationAt(1_200L));
    }

    @Test
    void rotationAtStartStaysWithinAmplitude() {
        Session.Shake shake = new Session.Shake(120f, -10f, 7.0, 1_000L, 200L);
        Session.Rotation rotation = shake.rotationAt(1_000L);
        assertTrue(Math.abs(rotation.yaw() - 120f) <= 7.0);
        assertTrue(Math.abs(rotation.pitch() + 10f) <= 7.0 * 0.65 + 1.0e-6);
    }

    @Test
    void pitchIsClampedToNinetyDegrees() {
        Session.Shake shake = new Session.Shake(0f, 89f, 7.0, 1_000L, 200L);
        assertTrue(shake.rotationAt(1_000L).pitch() <= 89f);
    }

    @Test
    void overlappingShakeKeepsOriginalBase() {
        Session.Shake original = new Session.Shake(120f, -10f, 3.0, 1_000L, 1_000L);
        Session.Shake overlap = new Session.Shake(200f, 20f, 7.0, 1_100L, 200L);
        Session.Shake layered = Session.Shake.layer(original, overlap);
        assertEquals(new Session.Rotation(120f, -10f), layered.rotationAt(1_300L));
        assertEquals(7.0, layered.amplitude, 1.0e-9);
    }

    @Test
    void expiredShakeDoesNotOverrideNewBase() {
        Session.Shake expired = new Session.Shake(10f, 5f, 3.0, 1_000L, 100L);
        Session.Shake fresh = new Session.Shake(90f, -20f, 5.0, 1_200L, 200L);
        Session.Shake layered = Session.Shake.layer(expired, fresh);
        assertEquals(new Session.Rotation(90f, -20f), layered.rotationAt(1_400L));
    }

    @Test
    void rotationAtBeforeStartMatchesStart() {
        Session.Shake shake = new Session.Shake(120f, -10f, 7.0, 1_000L, 200L);
        assertEquals(shake.rotationAt(1_000L), shake.rotationAt(900L));
    }
}
