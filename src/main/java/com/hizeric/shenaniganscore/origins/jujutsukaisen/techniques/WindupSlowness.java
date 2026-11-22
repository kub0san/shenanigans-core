package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques;

/**
 * Bookkeeping for a technique's windup root (SLOWNESS).
 *
 * <p>A technique that plants the caster's feet while it draws grants a short SLOWNESS and must
 * take exactly that one back when it releases or cancels — never a SLOWNESS from another
 * technique, a potion or a third-party plugin. The tracker records the applied effect's
 * duration/amplifier and any effect it replaced, so the caller can verify identity before
 * removing anything and restore the previous root.</p>
 *
 * <p>The tracker holds no Bukkit state: the technique calls {@link #grant} when it applies the
 * effect, and {@link #consume()} on every exit path (release <em>and</em> cancel).</p>
 */
public final class WindupSlowness {

    private boolean granted;
    private int duration = -1;
    private int amplifier = -1;
    private Integer previousDuration;
    private Integer previousAmplifier;

    /** Legacy no-signature grant: kept so older callers/tests still compile. */
    public void grant() {
        grant(-1, -1, null, null);
    }

    /** Records the exact effect applied and the one it replaced. */
    public void grant(int duration, int amplifier, Integer previousDuration, Integer previousAmplifier) {
        granted = true;
        this.duration = duration;
        this.amplifier = amplifier;
        this.previousDuration = previousDuration;
        this.previousAmplifier = previousAmplifier;
    }

    /** True while this technique's granted slowness is still outstanding. */
    public boolean granted() {
        return granted;
    }

    /**
     * True for the first call after a {@link #grant()}, so a release followed by a cancel never
     * strips twice. The recorded signature survives consumption so the caller can still verify
     * which effect is currently on the player.
     */
    public boolean consume() {
        if (!granted) return false;
        granted = false;
        return true;
    }

    /** Whether the live effect is still the one this technique applied (duration may have ticked). */
    public boolean matches(int currentDuration, int currentAmplifier) {
        if (amplifier < 0) return true; // legacy grant(): no signature to distinguish
        return currentAmplifier == amplifier && currentDuration <= duration;
    }

    public Integer previousDuration() {
        return previousDuration;
    }

    public Integer previousAmplifier() {
        return previousAmplifier;
    }
}
