package com.hizeric.shenaniganscore.battleground.deploy;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static com.hizeric.shenaniganscore.core.util.BlockKey.pack;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The convergence drop-zone seam. Every decision is made from a pure {@link DropProbe}, so the
 * geometry can be reasoned about without a live world: attraction toward anchors, mercy around
 * avoiders, structural stability, two-pass sampling and heightmap-capped release heights.
 */
class DropZoneTest {

    private enum Block { AIR, SOLID, LIQUID }

    private static final class FakeProbe implements DropProbe {
        private final Map<Long, Block> blocks = new HashMap<>();

        void set(int x, int y, int z, Block block) {
            blocks.put(pack(x, y, z), block);
        }

        /** One layer of surface blocks plus a solid support layer directly underneath. */
        void slab(int minX, int maxX, int minZ, int maxZ, int topY) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    set(x, topY, z, Block.SOLID);
                    set(x, topY - 1, z, Block.SOLID);
                }
            }
        }

        private Block at(int x, int y, int z) {
            return blocks.getOrDefault(pack(x, y, z), Block.AIR);
        }

        @Override
        public boolean isAirLike(int x, int y, int z) {
            return at(x, y, z) == Block.AIR;
        }

        @Override
        public boolean isLiquid(int x, int y, int z) {
            return at(x, y, z) == Block.LIQUID;
        }
    }

    private static DropZone.Config config() {
        return new DropZone.Config(1, -64, 40, 20, 3, 10, -64, 80, 12, 8, 5,
                List.of(
                        new DropZone.Tier(2, 0, 0.55),
                        new DropZone.Tier(4, 2, 0.30),
                        new DropZone.Tier(8, 6, 0.05),
                        new DropZone.Tier(Integer.MAX_VALUE, Integer.MAX_VALUE, -1e9)));
    }

    private static DropZone.Config thinFloorConfig(int floorY) {
        return new DropZone.Config(1, -64, floorY + 50, 20, 3, 10,
                floorY, floorY + 50, 12, 8, 5, config().tiers());
    }

    private static DropCohort cohort(DropZone.Point... anchors) {
        return new DropCohort(java.util.Arrays.stream(anchors)
                .map(p -> new DropCohort.Anchor(p.x(), p.z(), 1.0)).toList(), List.of());
    }

    private static DropZone.Selection select(FakeProbe probe, int lo, int hi, DropCohort cohort) {
        return DropZone.select(probe, lo, hi, lo, hi, cohort, config());
    }

    @Test
    void flatClearGroundProducesASpawnAboveItsOwnSurface() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-8, 8, -8, 8, 30);

        DropZone.Selection selection = select(probe, -8, 8, DropCohort.empty());

        assertTrue(selection.found(), "flat clear ground is a valid drop zone");
        assertEquals(30, selection.candidate().groundY());
        assertEquals(50, selection.candidate().spawnY(), "surface + drop height, never arena mid-Y");
        assertTrue(selection.candidate().spawnY() > selection.candidate().groundY());
    }

    @Test
    void supportedPlateauFollowsTheLocalSurface() {
        FakeProbe probe = new FakeProbe();
        // A supported 3x3 plateau standing alone; a single floating pillar would be rejected.
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                for (int y = 30; y <= 36; y++) probe.set(x, y, z, Block.SOLID);
            }
        }

        DropZone.Selection selection = select(probe, -8, 8, DropCohort.empty());

        assertTrue(selection.found());
        assertEquals(36, selection.candidate().groundY(), "the plateau top is the local surface");
        assertEquals(56, selection.candidate().spawnY());
        assertTrue(selection.candidate().spawnY() > 36 + 3);
    }

    @Test
    void roughLandingIsFoundByProgressiveRelaxation() {
        FakeProbe probe = new FakeProbe();
        for (int x = -8; x <= 8; x++) {
            int top = (Math.floorMod(x, 4) < 2) ? 30 : 27;
            probe.slab(x, x, -8, 8, top);
        }
        // Anchor outside the arena keeps attraction high so only the flatness cap decides the tier.
        DropCohort cohort = cohort(new DropZone.Point(20, 0));

        DropZone.Selection selection = select(probe, -8, 8, cohort);

        assertTrue(selection.found(), "rough ground must be scored, never hard-rejected");
        assertEquals(1, selection.tier(), "strict tier is empty, so the second tier must be used");
        assertEquals(3, selection.candidate().flatDeviation());
    }

    @Test
    void flatnessClarityAndAttributionAllContributeToTheScore() {
        long flat = DropZone.score(0, 0, 1.0);
        long rough = DropZone.score(3, 0, 1.0);
        long obstructed = DropZone.score(0, 4, 1.0);
        long unattractive = DropZone.score(0, 0, 0.0);

        assertTrue(flat > rough, "a flatter surface scores higher");
        assertTrue(flat > obstructed, "clearer air above the surface scores higher");
        assertTrue(flat > unattractive, "landing near an anchor scores higher");
    }

    @Test
    void scoreScalesWithAttraction() {
        assertTrue(DropZone.score(0, 0, 0.8) > DropZone.score(0, 0, 0.2));
    }

    @Test
    void aSurfaceHintAvoidsScanningTheWholeColumn() {
        FakeProbe base = new FakeProbe();
        base.slab(-10, 10, -10, 10, 30);
        int[] highestRead = {Integer.MIN_VALUE};
        DropProbe hinted = new DropProbe() {
            @Override
            public boolean isAirLike(int x, int y, int z) {
                highestRead[0] = Math.max(highestRead[0], y);
                return base.isAirLike(x, y, z);
            }

            @Override
            public boolean isLiquid(int x, int y, int z) {
                return base.isLiquid(x, y, z);
            }

            @Override
            public int surfaceY(int x, int z) {
                return 30;
            }
        };
        DropZone.Config tallTop = new DropZone.Config(1, -64, 200, 20, 3, 10,
                -64, 200, 12, 8, 5, config().tiers());
        DropZone.Selection selection = DropZone.select(hinted, -4, 4, -4, 4,
                DropCohort.empty(), tallTop);

        assertTrue(selection.found());
        assertTrue(highestRead[0] < 100, "a surface hint must replace the full-height scan");
    }

    @Test
    void engagementBandPullsDropsNearOtherPlayers() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-20, 20, -20, 20, 30);

        DropZone.Selection selection = select(probe, -20, 20, cohort(new DropZone.Point(0, 0)));

        assertTrue(selection.found());
        int distance = (int) Math.hypot(selection.candidate().x(), selection.candidate().z());
        assertTrue(distance >= 5 && distance <= 18,
                "the pick must sit in the engagement band around the anchor, got " + distance);
    }

    @Test
    void avoiderMercyRadiusPushesTheDropAwayFromTheLastKiller() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-20, 20, -20, 20, 30);
        DropCohort cohort = new DropCohort(
                List.of(new DropCohort.Anchor(0, 0, 1.0)),
                List.of(new DropZone.Point(12, 0)));

        DropZone.Selection selection = DropZone.select(
                probe, -20, 20, -20, 20, cohort, config(), new java.util.Random(1));

        assertTrue(selection.found());
        double toAnchor = Math.hypot(selection.candidate().x(), selection.candidate().z());
        double toKiller = Math.hypot(selection.candidate().x() - 12, selection.candidate().z());
        assertTrue(toKiller > toAnchor,
                "the drop must land farther from the last killer than from the anchor");
    }

    @Test
    void singleBlockPillarsAreRejected() {
        FakeProbe probe = new FakeProbe();
        probe.set(0, 30, 0, Block.SOLID);
        probe.set(0, 29, 0, Block.SOLID);

        DropZone.Selection selection = select(probe, -4, 4, DropCohort.empty());

        assertFalse(selection.found(), "a lone pillar has no lateral support and is not standable");
        assertEquals(DropZone.Reason.NO_SOLID_GROUND, selection.reason());
    }

    @Test
    void theWholeArenaIsScannedNotJustTheFirstRowMajorColumns() {
        FakeProbe probe = new FakeProbe();
        probe.slab(10, 11, 10, 11, 30);

        DropZone.Selection selection = select(probe, -12, 12, DropCohort.empty());

        assertTrue(selection.found(), "a single good clearing anywhere in the arena must be found");
        assertTrue(selection.candidate().x() >= 10 && selection.candidate().z() >= 10,
                "the distant clearing must be the selected spot");
    }

    @Test
    void aLiquidToppedColumnIsNeverSelected() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-2, 2, -2, 2, 30);
        probe.set(0, 31, 0, Block.LIQUID);

        DropZone.Selection selection = select(probe, -2, 2, DropCohort.empty());

        assertTrue(selection.found());
        assertFalse(selection.candidate().x() == 0 && selection.candidate().z() == 0,
                "the liquid-topped column cannot be a drop target");
    }

    @Test
    void anEmptyArenaReportsNoSolidGround() {
        DropZone.Selection selection = select(new FakeProbe(), -8, 8, DropCohort.empty());

        assertFalse(selection.found());
        assertEquals(DropZone.Reason.NO_SOLID_GROUND, selection.reason());
    }

    @Test
    void anAllLiquidArenaReportsAllLiquid() {
        FakeProbe probe = new FakeProbe();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) probe.set(x, 30, z, Block.LIQUID);
        }

        DropZone.Selection selection = select(probe, -8, 8, DropCohort.empty());

        assertFalse(selection.found());
        assertEquals(DropZone.Reason.ALL_LIQUID, selection.reason());
    }

    @Test
    void extremeCliffsFallThroughToTheLastResortTier() {
        FakeProbe probe = new FakeProbe();
        for (int x = -8; x <= 8; x++) {
            probe.slab(x, x, -8, 8, Math.floorMod(x, 4) < 2 ? 30 : 10);
        }

        DropZone.Selection selection = select(probe, -8, 8, DropCohort.empty());

        assertTrue(selection.found(), "the final tier never hard-rejects rough ground");
        assertEquals(3, selection.tier());
        assertEquals(20, selection.candidate().flatDeviation());
    }

    @Test
    void equalBestCandidatesAreTieBrokenRandomly() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-2, 2, -2, 2, 30);

        Set<Long> picked = new HashSet<>();
        for (int seed = 0; seed < 64; seed++) {
            DropZone.Selection selection = DropZone.select(probe, -2, 2, -2, 2,
                    DropCohort.empty(), config(), new Random(seed));
            assertTrue(selection.found(), "flat ground should always be selectable");
            picked.add(pack(selection.candidate().x(), 0, selection.candidate().z()));
        }

        assertTrue(picked.size() > 1, "equal-score candidates must rotate, got " + picked);
    }

    @Test
    void tieBreakIsDeterministicWithSeededRandom() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-2, 2, -2, 2, 30);

        DropZone.Selection a = DropZone.select(probe, -2, 2, -2, 2,
                DropCohort.empty(), config(), new Random(12345L));
        DropZone.Selection b = DropZone.select(probe, -2, 2, -2, 2,
                DropCohort.empty(), config(), new Random(12345L));

        assertTrue(a.found() && b.found());
        assertEquals(a.candidate().x(), b.candidate().x());
        assertEquals(a.candidate().z(), b.candidate().z());
    }

    @Test
    void anchorBestSpotIsStableAcrossSeeds() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-8, 8, -8, 8, 30);
        DropCohort cohort = cohort(new DropZone.Point(-8, -8));

        for (int seed = 0; seed < 16; seed++) {
            DropZone.Selection selection = DropZone.select(probe, -8, 8, -8, 8,
                    cohort, config(), new Random(seed));
            assertTrue(selection.found());
            double distance = Math.hypot(selection.candidate().x() + 8, selection.candidate().z() + 8);
            assertTrue(distance >= 5 && distance <= 18, "seed " + seed + " broke the engagement band");
        }
    }

    @Test
    void boxArenaCeilingIsSkippedAndInteriorFloorIsUsed() {
        FakeProbe base = new FakeProbe();
        base.slab(-4, 4, -4, 4, 61); // floor at 61 (support at 60)
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                base.set(x, 69, z, Block.SOLID); // ceiling support
                base.set(x, 70, z, Block.SOLID); // ceiling
            }
        }
        DropProbe hinted = new DropProbe() {
            @Override public boolean isAirLike(int x, int y, int z) { return base.isAirLike(x, y, z); }
            @Override public boolean isLiquid(int x, int y, int z) { return base.isLiquid(x, y, z); }
            @Override public int surfaceY(int x, int z) { return 70; } // heightmap sees the ceiling
        };
        DropZone.Config boxConfig = new DropZone.Config(1, 60, 70, 20, 3, 10,
                60, 70, 12, 8, 5, config().tiers());

        DropZone.Selection selection = DropZone.select(hinted, -4, 4, -4, 4,
                DropCohort.empty(), boxConfig, new Random(1));

        assertTrue(selection.found(), "the interior floor is a valid deploy target");
        assertEquals(61, selection.candidate().groundY(), "must land on the interior floor");
        assertTrue(selection.candidate().spawnY() < 70, "spawn must stay below the ceiling");
        assertTrue(selection.candidate().spawnY() > selection.candidate().groundY() + 1);
    }

    @Test
    void spawnIsClampedInsideRegionMaxY() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-2, 2, -2, 2, 10);
        DropZone.Config tight = new DropZone.Config(1, 0, 16, 20, 3, 10,
                0, 16, 12, 8, 5, config().tiers());

        DropZone.Selection selection = DropZone.select(probe, -2, 2, -2, 2,
                DropCohort.empty(), tight, new Random(1));

        assertTrue(selection.found());
        assertEquals(10, selection.candidate().groundY());
        assertTrue(selection.candidate().spawnY() <= 14, "spawn must stay within region max Y");
        assertTrue(selection.candidate().spawnY() > 11, "spawn must still allow a drop");
    }

    @Test
    void obstructionAboveClearanceBandStillLiftsSpawn() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-2, 2, -2, 2, 30);
        probe.set(0, 0, 45, Block.SOLID);

        DropZone.Selection selection = DropZone.select(probe, 0, 0, 0, 0,
                DropCohort.empty(), config(), new Random(1));

        assertTrue(selection.found());
        assertEquals(30, selection.candidate().groundY());
        assertTrue(selection.candidate().spawnY() > 45,
                "spawn must be above an obstruction above the clearance band");
    }

    @Test
    void thinFloorAtRegionMinYIsSelected() {
        FakeProbe probe = new FakeProbe();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) probe.set(x, 30, z, Block.SOLID);
        }

        DropZone.Selection selection = DropZone.select(probe, -4, 4, -4, 4,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertTrue(selection.found(), "a one-layer floor at region min Y is standable");
        assertEquals(30, selection.candidate().groundY());
        assertEquals(50, selection.candidate().spawnY());
    }

    @Test
    void thinFloorOverVoidIsSelected() {
        FakeProbe probe = new FakeProbe();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) probe.set(x, 30, z, Block.SOLID);
        }

        DropZone.Selection selection = DropZone.select(probe, -4, 4, -4, 4,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertTrue(selection.found(), "air below the floor layer must not disqualify it");
        assertEquals(30, selection.candidate().groundY());
    }

    @Test
    void caveBelowThinFloorIsIgnored() {
        FakeProbe probe = new FakeProbe();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) probe.set(x, 30, z, Block.SOLID);
        }
        probe.slab(-4, 4, -4, 4, 20);

        DropZone.Selection selection = DropZone.select(probe, -4, 4, -4, 4,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertTrue(selection.found());
        assertEquals(30, selection.candidate().groundY(), "the supported cave below is never the surface");
    }

    @Test
    void thinFloorWallTopCornersAreNotSelected() {
        FakeProbe probe = new FakeProbe();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) probe.set(x, 80, z, Block.SOLID);
        }
        for (int y = 80; y <= 95; y++) {
            for (int i = -8; i <= 8; i++) {
                probe.set(-8, y, i, Block.SOLID);
                probe.set(8, y, i, Block.SOLID);
                probe.set(i, y, -8, Block.SOLID);
                probe.set(i, y, 8, Block.SOLID);
            }
        }

        DropZone.Selection selection = DropZone.select(probe, -8, 8, -8, 8,
                DropCohort.empty(), thinFloorConfig(80), new Random(1));

        assertTrue(selection.found(), "the interior thin floor is still selectable");
        assertEquals(80, selection.candidate().groundY());
        assertTrue(Math.abs(selection.candidate().x()) < 8 && Math.abs(selection.candidate().z()) < 8,
                "wall-top columns must not be the landing spot");
        // Strengthen: no seed may select a wall-ring column while an interior candidate exists.
        for (int seed = 1; seed <= 50; seed++) {
            DropZone.Selection seeded = DropZone.select(probe, -8, 8, -8, 8,
                    DropCohort.empty(), thinFloorConfig(80), new Random(seed));
            assertTrue(seeded.found(), "interior floor exists, so something must be selected, seed " + seed);
            assertEquals(80, seeded.candidate().groundY(), "seed " + seed);
            assertTrue(Math.abs(seeded.candidate().x()) < 8 && Math.abs(seeded.candidate().z()) < 8,
                    "wall-top columns must not be the landing spot, seed " + seed);
        }
    }

    @Test
    void loneSolidBlockAtFloorPlaneIsRejected() {
        FakeProbe probe = new FakeProbe();
        probe.set(0, 30, 0, Block.SOLID);

        DropZone.Selection selection = DropZone.select(probe, -4, 4, -4, 4,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertFalse(selection.found(), "a lone block still lacks lateral support");
    }

    @Test
    void floatingPlatformAboveFloorPlaneIsStillRejected() {
        FakeProbe probe = new FakeProbe();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) probe.set(x, 40, z, Block.SOLID);
        }

        DropZone.Selection selection = DropZone.select(probe, -4, 4, -4, 4,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertFalse(selection.found(), "relaxation applies only at the floor plane, not above it");
    }

    @Test
    void liquidToppedThinFloorIsNotSelected() {
        FakeProbe probe = new FakeProbe();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) probe.set(x, 30, z, Block.SOLID);
        }
        probe.set(0, 31, 0, Block.LIQUID);

        DropZone.Selection selection = DropZone.select(probe, -2, 2, -2, 2,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertTrue(selection.found());
        assertFalse(selection.candidate().x() == 0 && selection.candidate().z() == 0,
                "a liquid-topped column cannot be the drop target");
    }

    @Test
    void thinFloorPatchNearBoundaryIsFound() {
        FakeProbe probe = new FakeProbe();
        for (int x = 10; x <= 12; x++) {
            for (int z = 10; z <= 12; z++) probe.set(x, 30, z, Block.SOLID);
        }

        DropZone.Selection selection = DropZone.select(probe, -12, 12, -12, 12,
                DropCohort.empty(), thinFloorConfig(30), new Random(1));

        assertTrue(selection.found(), "a small thin-floor patch must be found despite the coarse stride");
        assertTrue(selection.candidate().x() >= 10 && selection.candidate().x() <= 12
                        && selection.candidate().z() >= 10 && selection.candidate().z() <= 12,
                "the selected spot lies in the patch");
    }

    @Test
    void hasThinFloorDetectsOneLayerFloor() {
        FakeProbe probe = new FakeProbe();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) probe.set(x, 30, z, Block.SOLID);
        }

        assertTrue(DropZone.hasThinFloor(probe, -4, 4, -4, 4, 30, 1),
                "a one-layer floor must be detected");
    }

    @Test
    void hasThinFloorIgnoresSupportedFloor() {
        FakeProbe probe = new FakeProbe();
        probe.slab(-4, 4, -4, 4, 30);

        assertFalse(DropZone.hasThinFloor(probe, -4, 4, -4, 4, 30, 1),
                "a floor with a support layer is not thin");
    }

    @Test
    void hasThinFloorIgnoresLiquidSurface() {
        FakeProbe probe = new FakeProbe();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) probe.set(x, 30, z, Block.LIQUID);
        }

        assertFalse(DropZone.hasThinFloor(probe, -4, 4, -4, 4, 30, 1),
                "a liquid surface is not a thin solid floor");
    }
}
