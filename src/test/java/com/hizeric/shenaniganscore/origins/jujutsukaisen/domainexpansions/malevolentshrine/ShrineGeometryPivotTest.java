package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Issue #75: a display box is scaled first and then rotated about its centre,
 * so the lowest point of a leaned prop is its pivot Y minus the rotated
 * half-height, not the unleaned {@code scaleY / 2}.
 */
class ShrineGeometryPivotTest {

    @Test
    void rotatedHalfHeightUsesBothAxesAndKeepsTheCentreAtThePivot() {
        // No lean: just half the vertical scale.
        assertEquals(0.425, ShrineGeometry.rotatedHalfExtentY(0.85, 0.95, 0.0), 1e-12);
        // Skull: sy=0.85, sxz=0.95, leanZ=8 -> (0.85*cos8 + 0.95*sin8)/2
        assertEquals(0.48697115217119846, ShrineGeometry.rotatedHalfExtentY(0.85, 0.95, 8.0), 1e-12);
        // Long bone: sy=1.5, sxz=0.3, leanX=78 -> (1.5*cos78 + 0.3*sin78)/2
        assertEquals(0.30265590822339045, ShrineGeometry.rotatedHalfExtentY(1.5, 0.3, 78.0), 1e-12);
    }
}
