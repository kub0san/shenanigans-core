package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

/**
 * Pure buff decisions for the Malevolent Shrine domain.
 *
 * <p>Kept free of Bukkit types so the grant/strip rules can be unit-tested
 * without a live {@link org.bukkit.entity.Player}. {@code Session} reads the
 * caster's current Resistance, feeds the numbers here, and applies the effect
 * (or removes it) on the server thread.</p>
 */
final class ShrineSessionDecision {

    /** Length of the delayed RAVAGE choreography in milliseconds. */
    static final long RAVAGE_WINDOW_MS = 900L;

    private ShrineSessionDecision() {
    }

    /**
     * Whether a delayed RAVAGE runnable should keep running. A domain that has
     * ended (or is being force-closed) must not spawn new slash stages into an
     * arena that is already restoring itself. A negative elapsed time is the
     * per-victim stagger and is still allowed to wait.
     */
    static boolean shouldKeepRavageRunnable(boolean sessionActive, long elapsedMs) {
        return sessionActive && elapsedMs < RAVAGE_WINDOW_MS;
    }

    /**
     * Whether the domain should (re)grant its Resistance II.
     *
     * <p>Only when the caster has no Resistance, or the one they have is weaker
     * than Resistance II, or it will expire before the domain does. Leaving an
     * equal-or-stronger, longer-lasting effect alone is what stops {@link #shouldRemoveResistance}
     * from stripping a buff the domain never granted.</p>
     *
     * @param existingAmplifier    current Resistance amplifier, or {@code -1} when absent
     * @param existingDurationTicks current Resistance duration in ticks (ignored when absent)
     * @param domainDurationTicks   how long the domain's Resistance would last
     */
    static boolean shouldGrantResistance(int existingAmplifier, int existingDurationTicks, int domainDurationTicks) {
        return existingAmplifier < 1 || existingDurationTicks < domainDurationTicks;
    }

    /** Whether {@code close()} should remove the caster's Resistance — only when the domain granted it. */
    static boolean shouldRemoveResistance(boolean domainGrantedResistance) {
        return domainGrantedResistance;
    }
}