package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

/**
 * Pure barrier-front math for Time Cell Moon Palace.
 *
 * <p>The 24 FPS clock drives film visuals and the sure-hit rule; it no longer
 * quantises the barrier's formation front. Floor and shell therefore advance
 * continuously while the film itself still reads in whole frames.</p>
 */
final class FormationProgress {

    private FormationProgress() {
    }

    /** Continuous 0–1 formation front, clamped only at the endpoints. */
    static double continuous(double raw) {
        if (!(raw > 0.0)) return 0.0;
        return Math.min(1.0, raw);
    }
}
