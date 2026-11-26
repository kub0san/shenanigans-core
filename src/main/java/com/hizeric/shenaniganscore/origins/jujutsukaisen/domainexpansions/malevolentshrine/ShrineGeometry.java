package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

/**
 * Pure geometry seam for the Malevolent Shrine.
 *
 * <p>Free of Bukkit types and static display state so the ground datum can be
 * unit-tested without a running server.</p>
 */
final class ShrineGeometry {

    private ShrineGeometry() {
    }

    /**
     * Ground datum for shrine-local geometry. The shrine centre is the caster's
     * footing + 1, while the real platform surface is one block below it; local
     * {@code y = 0} is authored at that surface.
     */
    static double groundContactY(double shrineCenterY, double localY) {
        return shrineCenterY - 1.0 + localY;
    }

    /** A block display pivots its unit block at local (0.5, 0.5, 0.5). */
    static final double BLOCK_PIVOT_Y = 0.5;

    /**
     * Bottom face of a display block centred at {@code centerLocalY} with
     * vertical scale {@code scaleY}. Block geometry runs from local 0 (bottom)
     * to local 1 (top), so scaling around the pivot leaves the bottom at
     * {@code centerLocalY + 0.5 - scaleY / 2}. A lean can shift the lowest
     * corner either way depending on the other axis scales; this helper is for
     * the axis-aligned (yaw-only) case.
     */
    static double bottomFaceLocalY(double centerLocalY, double scaleY) {
        return centerLocalY + BLOCK_PIVOT_Y - scaleY * 0.5;
    }

    /**
     * Converts a centre authored under the old "bottom = centre - scale / 2"
     * convention into the centre that actually seats the block as intended.
     */
    static double centerFromAuthoredY(double authoredCenterLocalY) {
        return authoredCenterLocalY - BLOCK_PIVOT_Y;
    }

    /**
     * Half-height of a box scaled by {@code scaleY} vertically and
     * {@code scaleXZ} on the horizontal axis that gets leaned, then rotated by
     * {@code leanDegrees}. The box is scaled first and rotated about its centre,
     * so the centre stays at the pivot and the lowest point is that centre
     * minus this extent. A square footprint is assumed; both leaned bone-pile
     * props have equal x/z scales.
     */
    static double rotatedHalfExtentY(double scaleY, double scaleXZ, double leanDegrees) {
        double rad = Math.toRadians(leanDegrees);
        return (scaleY * Math.abs(Math.cos(rad)) + scaleXZ * Math.abs(Math.sin(rad))) * 0.5;
    }
}
