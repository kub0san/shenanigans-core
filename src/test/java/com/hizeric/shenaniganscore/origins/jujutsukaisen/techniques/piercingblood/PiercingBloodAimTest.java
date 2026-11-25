package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #69: the shot beam must leave the fingertips on the crosshair ray, and its direction must
 * never snap or flip when the aim point is close to — or behind — the muzzle. Pure vectors only;
 * no server needed.
 */
class PiercingBloodAimTest {

    private static final Vector AIM = new Vector(0.6, 0.25, -0.75).normalize();

    @Test
    void beamMuzzleIsCollinearWithAndAheadOfTheEyeRay() {
        Location eye = new Location(null, 10, 64, -3);
        Vector forward = new Vector(0.4, -0.2, 0.9).normalize();
        Vector forwardBefore = forward.clone();

        Location muzzle = PiercingBloodAim.beamMuzzle(eye, forward);
        Vector offset = muzzle.toVector().subtract(eye.toVector());

        assertEquals(2.35, offset.length(), 1.0e-9, "the beam muzzle sits 2.35 blocks out");
        assertEquals(0.0, offset.clone().crossProduct(forward).length(), 1.0e-9,
                "the beam muzzle lies on the crosshair ray");
        assertTrue(offset.dot(forward) > 0.0, "the beam muzzle is ahead of the eye");
        assertEquals(64.0, eye.getY(), 1.0e-9, "the eye must not be mutated");
        assertEquals(forwardBefore, forward, "forward must not be mutated");
    }

    @Test
    void collinearAimPointsAtFourAndFourPointFourDoNotSnap() {
        Location eye = new Location(null, 0, 70, 0);
        Vector muzzle = PiercingBloodAim.beamMuzzle(eye, AIM).toVector();

        for (double distance : new double[] {4.0, 4.4}) {
            Vector aimPoint = eye.clone().add(AIM.clone().multiply(distance)).toVector();
            Vector dir = PiercingBloodAim.direction(AIM, muzzle, aimPoint);

            assertEquals(1.0, dir.length(), 1.0e-9, "direction at D=" + distance + " is normalized");
            assertEquals(0.0, dir.clone().crossProduct(AIM).length(), 1.0e-9,
                    "direction at D=" + distance + " stays on the crosshair ray");
            assertTrue(dir.dot(AIM) > 0.0, "direction at D=" + distance + " stays forward");
        }
    }

    @Test
    void aimPointBehindTheMuzzleStillFiresForward() {
        Location eye = new Location(null, 0, 70, 0);
        Vector muzzle = PiercingBloodAim.beamMuzzle(eye, AIM).toVector();
        Vector behind = eye.toVector();
        Vector aimBefore = AIM.clone();
        Vector muzzleBefore = muzzle.clone();
        Vector behindBefore = behind.clone();

        Vector dir = PiercingBloodAim.direction(AIM, muzzle, behind);

        assertTrue(dir.dot(AIM) > 0.0, "a backwards aim point must not reverse the shot");
        assertEquals(1.0, dir.length(), 1.0e-9);
        assertEquals(aimBefore, AIM, "aimDir must not be mutated");
        assertEquals(muzzleBefore, muzzle, "muzzle must not be mutated");
        assertEquals(behindBefore, behind, "aimPoint must not be mutated");
    }

    @Test
    void shortRangeAimFallsBackToTheAimDirection() {
        Vector muzzle = new Vector(0, 0, 0);
        Vector aimPoint = AIM.clone().multiply(0.5);

        Vector dir = PiercingBloodAim.direction(AIM, muzzle, aimPoint);

        assertEquals(0.0, dir.clone().crossProduct(AIM).length(), 1.0e-9,
                "inside the short-range guard the aim direction wins");
        assertTrue(dir.dot(AIM) > 0.0);
        assertEquals(1.0, dir.length(), 1.0e-9);
    }

    @Test
    void offAxisAimReturnsANormalizedVectorWithoutMutatingInputs() {
        Vector aimDir = new Vector(0, 0, 1);
        Vector muzzle = new Vector(1, 2, 3);
        Vector aimPoint = muzzle.clone().add(new Vector(5, 2, 40));
        Vector aimBefore = aimDir.clone();
        Vector muzzleBefore = muzzle.clone();
        Vector aimPointBefore = aimPoint.clone();

        Vector dir = PiercingBloodAim.direction(aimDir, muzzle, aimPoint);

        assertEquals(1.0, dir.length(), 1.0e-9);
        assertTrue(dir.dot(aimDir) > 0.0);
        assertNotEquals(aimDir, dir, "an off-axis aim point must bend the beam");
        assertEquals(aimBefore, aimDir, "aimDir must not be mutated");
        assertEquals(muzzleBefore, muzzle, "muzzle must not be mutated");
        assertEquals(aimPointBefore, aimPoint, "aimPoint must not be mutated");
    }
}
