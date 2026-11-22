package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The caster-buff grant/strip decisions for the Malevolent Shrine domain.
 *
 * <p>The domain grants the caster Resistance II for its duration and, on close,
 * takes "its" Resistance back. The historical bug: {@code close()} removed any
 * Resistance II the caster happened to be holding, even one the domain never
 * granted (it skips granting when the caster already has an equal-or-stronger
 * effect that will outlast the domain). A caster who walked in with their own
 * Resistance II lost it when the domain ended.</p>
 *
 * <p>The fix is to grant only when the domain's buff would actually improve on
 * what is there, remember that it granted one, and remove only then. These are
 * the pure decisions that govern it; the {@code PotionEffect} plumbing itself
 * needs a live {@link org.bukkit.entity.Player} and is exercised in-game.</p>
 */
class ShrineResistanceBuffTest {

    /** Amplifier sentinel for "the caster has no Resistance effect at all". */
    private static final int NONE = -1;

    @Test
    void grantsWhenTheCasterHasNoResistance() {
        assertTrue(ShrineSessionDecision.shouldGrantResistance(NONE, 0, 200),
                "a caster with no Resistance must be granted the domain's Resistance II");
    }

    @Test
    void grantsOverAWeakerShortResistance() {
        assertTrue(ShrineSessionDecision.shouldGrantResistance(0, 40, 200),
                "a weaker (Resistance I) or short Resistance must be upgraded to the domain's");
    }

    @Test
    void upgradesAShortResistanceIi() {
        assertTrue(ShrineSessionDecision.shouldGrantResistance(1, 40, 200),
                "a Resistance II that expires before the domain ends must be replaced");
    }

    @Test
    void doesNotGrantOverAnEqualOrStrongerLastingResistance() {
        assertFalse(ShrineSessionDecision.shouldGrantResistance(1, 400, 200),
                "a Resistance II that already outlasts the domain must be left alone (so it is not stripped on close)");
        assertFalse(ShrineSessionDecision.shouldGrantResistance(2, 400, 200),
                "a Resistance III+ must be left alone");
    }

    @Test
    void removesResistanceOnlyWhenTheDomainGrantedIt() {
        assertTrue(ShrineSessionDecision.shouldRemoveResistance(true),
                "a Resistance the domain granted must be removed on close");
        assertFalse(ShrineSessionDecision.shouldRemoveResistance(false),
                "a Resistance the domain did NOT grant must be preserved on close");
    }
}