package com.hizeric.shenaniganscore.core.display;

/**
 * Pure spawn-budget math for {@link ShellDome}.
 *
 * <p>The dome's plate plan is unchanged; only the number of plates materialised
 * in one tick is bounded.</p>
 */
final class ShellDomePlan {

    /** Plates materialised per tick while the dome is being shown. */
    static final int PLATES_PER_TICK = 24;

    private ShellDomePlan() {
    }

    /** How many plates from {@code planned} may be spawned when {@code spawned} already exist. */
    static int batch(int planned, int spawned) {
        if (planned <= 0 || spawned < 0 || spawned >= planned) return 0;
        int remaining = planned - spawned;
        return Math.min(PLATES_PER_TICK, remaining);
    }
}
