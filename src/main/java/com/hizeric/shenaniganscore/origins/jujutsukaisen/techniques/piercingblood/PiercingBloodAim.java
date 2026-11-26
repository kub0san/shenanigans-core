package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood;

import org.bukkit.Location;
import org.bukkit.util.Vector;

/**
 * Pure aim math for Piercing Blood's beam (issue #69). The gauntlets pose below the crosshair, but
 * the beam itself leaves from a point on the crosshair ray and always points forward.
 */
public final class PiercingBloodAim {

    /** How far along the crosshair ray the beam starts. */
    private static final double MUZZLE_DISTANCE = 2.35;

    /** The old short-range guard: inside it, the eye-ray aim direction is used verbatim. */
    private static final double MIN_AIM_DISTANCE_SQ = 4.0;

    private PiercingBloodAim() {}

    /**
     * The beam origin on the eye ray. Kept separate from the hand pose so the shot starts aligned
     * with the crosshair even though the fingertips are drawn slightly below it.
     */
    public static Location beamMuzzle(Location eye, Vector forward) {
        return eye.clone().add(forward.clone().multiply(MUZZLE_DISTANCE));
    }

    /**
     * Resolves a normalized, forward-facing shot direction. A short or behind-the-muzzle aim point
     * must not snap the beam or flip it backwards; off-axis points still converge on the aim point.
     * Never mutates the caller's vectors.
     */
    public static Vector direction(Vector aimDir, Vector muzzle, Vector aimPoint) {
        Vector toAim = aimPoint.clone().subtract(muzzle);
        if (toAim.lengthSquared() <= MIN_AIM_DISTANCE_SQ || toAim.dot(aimDir) <= 0.0) {
            return aimDir.clone().normalize();
        }
        return toAim.normalize();
    }
}
