package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #75: ground-contact props sit on the real platform surface, not a
 * half-block above it. The shrine centre is the caster's footing + 1 (65 here),
 * the floor surface is one block lower (64), and a block display pivots its
 * unit block at local (0.5, 0.5, 0.5): a block centred at local {@code c} with
 * vertical scale {@code s} has its bottom face at local {@code c + 0.5 - s/2}.
 */
class ShrineGroundContactTest {

    private static final double SHRINE_CENTER_Y = 65.0;
    private static final double FLOOR_Y = ShrineGeometry.groundContactY(SHRINE_CENTER_Y, 0.0);
    private static final double TOLERANCE = 0.1;

    /** World bottom face of a piece whose real centre is {@code centerLocalY}. */
    private static double bottomY(double centerLocalY, double scaleY) {
        return ShrineGeometry.groundContactY(SHRINE_CENTER_Y,
                ShrineGeometry.bottomFaceLocalY(centerLocalY, scaleY));
    }

    /** World bottom face of a piece authored with the old centre = bottom + s/2 convention. */
    private static double authoredBottomY(double authoredCenterLocalY, double scaleY) {
        return bottomY(ShrineGeometry.centerFromAuthoredY(authoredCenterLocalY), scaleY);
    }

    /** World bottom face of an authored piece whose box is leaned about its pivot. */
    private static double leanedBottomY(double authoredCenterLocalY, double scaleY,
                                        double scaleXZ, double leanDegrees) {
        double pivotLocalY = ShrineGeometry.centerFromAuthoredY(authoredCenterLocalY)
                + ShrineGeometry.BLOCK_PIVOT_Y;
        return ShrineGeometry.groundContactY(SHRINE_CENTER_Y,
                pivotLocalY - ShrineGeometry.rotatedHalfExtentY(scaleY, scaleXZ, leanDegrees));
    }

    @Test
    void plainLocalZeroPropRestsOnTheFloorSurface() {
        // entity y = 65 - 1 + 0 = 64; bottom = 64 + 0.5 - 1/2 = 64 = floor.
        assertEquals(FLOOR_Y, bottomY(0.0, 1.0), TOLERANCE);
    }

    @Test
    void pillarPlinthSeatsOnTheFoundationAndTheShaftMeetsThePlinth() {
        double footY = Math.max(Builder.PILLAR_FOOT_MIN_Y, 0.0); // flat platform: terrainOffset = 0
        double plinthBottom = bottomY(footY + Builder.PILLAR_PLINTH_CENTER_OFFSET_Y,
                Builder.PILLAR_PLINTH_SCALE_Y);
        // Foundation slab: centre -1.5, scale 5 -> bottom -3.5, top 1.5 local.
        double foundationBottom = bottomY(Builder.FOUNDATION_BASE_CENTER_Y,
                Builder.FOUNDATION_BASE_SCALE_Y);
        double foundationTop = foundationBottom + Builder.FOUNDATION_BASE_SCALE_Y;

        assertEquals(foundationTop, plinthBottom, TOLERANCE,
                "plinth bottom must meet the foundation top, not float above it");
        assertTrue(foundationBottom < FLOOR_Y - 3.0,
                "the foundation underside must stay buried below the floor surface");
        assertTrue(plinthBottom > FLOOR_Y + 1.0,
                "the terrace is raised above the floor by design");

        // Shaft bottom (real) = footY + 0.9 + 0.5; plinth top = plinth bottom + 0.9.
        double shaftFoot = ShrineGeometry.groundContactY(SHRINE_CENTER_Y,
                footY + Builder.PILLAR_SHAFT_FOOT_OFFSET_Y + ShrineGeometry.BLOCK_PIVOT_Y);
        assertEquals(plinthBottom + Builder.PILLAR_PLINTH_SCALE_Y, shaftFoot, TOLERANCE,
                "shaft foot must continue the plinth without a visible seam");
    }

    @Test
    void bullSkullPedestalFootSinksBelowTheFloorAndStillSupportsTheSkull() {
        double terrain = 0.0; // inside the flat platform
        double footY = terrain + Builder.BULL_PEDESTAL_FOOT_BELOW_TERRAIN; // -0.85
        double height = Math.max(0.8, Builder.BULL_PEDESTAL_HEAD_DATUM_Y - footY); // 1.85
        double centerLocalY = footY + height * 0.5; // 0.075
        double pedestalBottom = bottomY(centerLocalY, height);
        double pedestalTop = pedestalBottom + height;

        // bottom = 0.075 + 0.5 - 1.85/2 = -0.35 local => world 63.65.
        assertEquals(FLOOR_Y - 0.35, pedestalBottom, TOLERANCE);
        assertTrue(pedestalBottom <= FLOOR_Y + TOLERANCE, "pedestal must not float");

        // Skull cap (centre 1.15, scale 0.3) still meets the unchanged datum top.
        assertEquals(bottomY(1.15, 0.3), pedestalTop, TOLERANCE,
                "lowering the foot must not detach the skull cap");
    }

    @Test
    void bonePilePiecesDoNotFloatAboveTheFloor() {
        // Skull: authored centre 0.45, sy 0.85, sxz 0.95, leanZ 8 ->
        // half = (0.85*cos8 + 0.95*sin8)/2 = 0.486971152; bottom 0.45 - 0.486971
        // = -0.036971 local => world 63.963029. The lean sinks it ~0.062
        // below the unleaned 64.025, so this prop is seated, not floating.
        double skull = leanedBottomY(Builder.BONE_SKULL_CENTER_Y, Builder.BONE_SKULL_SCALE_Y,
                Builder.BONE_SKULL_SCALE_XZ, Builder.BONE_SKULL_LEAN_Z_DEGREES);
        // Jaw: authored 0.08, scale 0.3, no lean -> bottom 0.08 - 0.3/2 = -0.07.
        double jaw = authoredBottomY(Builder.BONE_JAW_CENTER_Y, 0.3);
        // Long bone: authored centre 0.2, sy 1.5, sxz 0.3, leanX 78 ->
        // half = (1.5*cos78 + 0.3*sin78)/2 = 0.302655908; bottom 0.2 - 0.302656
        // = -0.102656 local => world 63.897344. Margin to floor is only
        // 0.102656 blocks, so assert the *leaned* value, not the unleaned 63.45.
        double longBone = leanedBottomY(Builder.BONE_LONG_BONE_CENTER_Y,
                Builder.BONE_LONG_BONE_SCALE_Y, Builder.BONE_LONG_BONE_SCALE_XZ,
                Builder.BONE_LONG_BONE_LEAN_X_DEGREES);

        // Golden values use Builder's float scales, widened to double.
        assertEquals(63.963028836853425, skull, 1e-12);
        assertEquals(63.8973440859464, longBone, 1e-12);
        assertTrue(skull <= FLOOR_Y + TOLERANCE, "bone-pile skull floats above the floor");
        assertTrue(jaw <= FLOOR_Y + TOLERANCE, "bone-pile jaw floats above the floor");
        assertTrue(longBone <= FLOOR_Y - 0.05,
                "the leaned long bone must genuinely sink below the floor surface");
        assertTrue(longBone > FLOOR_Y - 1.0,
                "the leaned long bone must not be buried out of sight");
    }
}
