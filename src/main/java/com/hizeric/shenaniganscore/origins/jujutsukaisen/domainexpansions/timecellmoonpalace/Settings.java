package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Every tunable of Time Cell Moon Palace. These are the shipped defaults, now
 * hardcoded — read once per cast so a running domain never changes behaviour mid-fight.
 */
final class Settings {

    /** The rule's namesake. The frame clock runs at this rate regardless of server TPS. */
    static final double FPS = 24.0;

    /** Lore: the domain cannot target someone with no cursed energy (Maki). Permission-based nod. */
    static final String IMMUNE_PERMISSION = "shenaniganscore.timecellmoonpalace.immune";

    // ── Timeline (seconds) ──
    final double signSeconds;
    final double expandSeconds;
    final double domainSeconds;
    final double collapseSeconds;

    // ── Domain behaviour ──
    final boolean casterBuffs;
    final boolean barrierLock;

    // ── Cinematics (turn off for weaker clients / servers) ──
    /** The one-second cut to black as the barrier seals, for everyone but the caster. */
    final boolean cutToBlack;
    /** The eye's flickering projector beam (about 600 particles a second). */
    final boolean projectorBeam;

    // ── The 24 FPS rule ──
    /** Freeze-meter (0-100) gained per block moved. */
    final double meterPerBlock;
    /** Meter lost per second while standing still. */
    final double meterDecay;
    /** Bleed damage (HP) per block moved. */
    final double bleedPerBlock;
    /** Bleed damage (HP) per second while frozen (the cuts keep opening). */
    final double frozenBleed;
    /** How long a full meter freezes the cells. */
    final double freezeSeconds;
    /** Free movement after thawing, so a victim is never frozen again instantly. */
    final double graceSeconds;
    /** Extra meter / bleed for one arm swing. */
    final double swingMeter;
    final double swingBleed;
    /** Limb severing: % of max health, cooldown, and whether a severed hand drops the held item. */
    final double severPercent;
    final double severCooldown;
    final boolean dropOnSever;
    /** Every Nth frame of the 24 FPS clock a moving victim's step is dropped. 0 = off. */
    final int stutterEvery;

    private Settings() {
        // Hardcoded to the shipped defaults.
        signSeconds = 1.0;
        expandSeconds = 6.0;
        domainSeconds = 16.0;
        collapseSeconds = 10.0;

        casterBuffs = true;
        barrierLock = true;
        cutToBlack = true;
        projectorBeam = true;

        meterPerBlock = 12.0;
        meterDecay = 25.0;
        bleedPerBlock = 0.75;
        frozenBleed = 0.8;
        freezeSeconds = 4.0;
        graceSeconds = 1.0;
        swingMeter = 15.0;
        swingBleed = 1.0;
        severPercent = MathUtil.clamp(25.0, 1.0, 100.0);
        severCooldown = 2.0;
        dropOnSever = true;
        stutterEvery = Math.max(0, 5);
    }

    static Settings load() {
        return new Settings();
    }
}
