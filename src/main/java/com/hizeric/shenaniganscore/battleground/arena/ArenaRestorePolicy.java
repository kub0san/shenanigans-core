package com.hizeric.shenaniganscore.battleground.arena;

/**
 * Restore finalisation policy, hardcoded in Java.
 *
 * <p>The arena YAMLs are version stubs, so these values are constants rather than
 * config keys. {@link #MANUAL_RELIGHT} must stay {@code false}: WorldEdit/FAWE owns
 * lighting during the paste, and a second relight pass over the same blocks was one
 * of the two causes of the first build's restore failures.</p>
 */
public final class ArenaRestorePolicy {

    /** Chunks re-sent to clients per restore tick. */
    public static final int CHUNKS_PER_TICK = 4;

    /** Pasted fluid blocks nudged per restore tick. */
    public static final int FLUID_NUDGES_PER_TICK = 512;

    /** Re-send every chunk in the region after the paste. */
    public static final boolean RESEND_CHUNKS = true;

    /** Re-apply pasted water/lava with physics so it re-evaluates its level. */
    public static final boolean NUDGE_FLUIDS = true;

    /** FAWE owns relighting; a manual relight pass is never run. */
    public static final boolean MANUAL_RELIGHT = false;

    private ArenaRestorePolicy() { }
}
