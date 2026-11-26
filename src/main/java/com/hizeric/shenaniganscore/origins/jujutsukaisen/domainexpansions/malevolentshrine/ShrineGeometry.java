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
}
