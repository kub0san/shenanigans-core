package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure geometry for the Rift Cleave timeline — no server required. */
class DashAnimationTest {

    @Test
    void rightOfUsesAFallbackForVerticalForward() {
        assertEquals(new Vector(1, 0, 0), DashAnimation.rightOf(new Vector(0, 1, 0)));
    }

    @Test
    void rightOfIsPerpendicularToForward() {
        Vector forward = new Vector(0, 0, 1);
        assertEquals(0.0, forward.dot(DashAnimation.rightOf(forward)), 1.0e-9);
    }

    @Test
    void beatsCoverTheSpecifiedTicks() {
        assertTrue(DashAnimation.beat(DashAnimation.Kind.CLEAVE_BURST).fires(0));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.CLEAVE_BURST).fires(2));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.CLEAVE_BURST).fires(3));

        assertTrue(DashAnimation.beat(DashAnimation.Kind.RIFT_RIBBON).fires(1));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.RIFT_RIBBON).fires(14));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.RIFT_RIBBON).fires(15));

        assertFalse(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(0));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(1));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(2));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(3));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(4));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(7));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(10));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(13));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(16));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(17));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(19));
    }

    @Test
    void trailSpawnsOneEchoEveryThreeTicksFromOneToSixteen() {
        assertEquals(1, DashAnimation.GHOST_COUNT);
        List<DashAnimation.Frame> echoFrames = DashAnimation.trail().stream()
                .filter(frame -> frame.spawns().stream()
                        .anyMatch(spawn -> spawn.kind() == DashAnimation.Kind.GHOST_ARC))
                .toList();
        assertEquals(List.of(1, 4, 7, 10, 13, 16),
                echoFrames.stream().map(DashAnimation.Frame::tick).toList());
        echoFrames.forEach(frame -> frame.spawns().forEach(
                spawn -> assertEquals(1, spawn.count())));
    }

    @Test
    void launchAndTrailProduceTheirFrameRanges() {
        assertEquals(21, DashAnimation.launch().size());
        assertEquals(DashAnimation.TRAIL_TICKS + 1, DashAnimation.trail().size());
    }

    @Test
    void windChargeRingsAreStaggeredAtZeroThreeAndSix() {
        List<Integer> ticks = DashAnimation.launch().stream()
                .filter(frame -> frame.spawns().stream()
                        .anyMatch(spawn -> spawn.kind() == DashAnimation.Kind.WIND_CHARGE))
                .map(DashAnimation.Frame::tick)
                .toList();
        assertEquals(List.of(0, 3, 6), ticks);
    }

    @Test
    void soundsFireAtZeroOneAndTenAndNeverInTheClampedTail() {
        List<Integer> ticks = DashAnimation.launch().stream()
                .filter(frame -> !frame.sounds().isEmpty())
                .map(DashAnimation.Frame::tick)
                .toList();
        assertEquals(List.of(0, 1, 10), ticks);

        for (int tick = 21; tick <= DashAnimation.TRAIL_TICKS; tick++) {
            assertTrue(DashAnimation.soundsForTick(tick).isEmpty(),
                    "clamped tail tick " + tick + " must not replay a sound");
        }
    }

    @Test
    void trailDistanceUsesAccumulatedDecayedDistance() {
        double d = DashMovement.distanceAfter(16, 2.4, 0.035);
        assertEquals(d, DashAnimation.trailDistance(d, 999.0), 1e-9);
    }

    @Test
    void trailDistanceStillClampsToRealizedTravel() {
        assertEquals(1.5, DashAnimation.trailDistance(9.0, 1.5), 1e-9);
    }

    @Test
    void echoFacingFollowsTheBeatHeading() {
        Vector north = new Vector(0, 0, 1);
        assertEquals(0.0f, DashAnimation.echoYaw(north), 1e-6f);
        assertEquals(0.0f, DashAnimation.echoPitch(north), 1e-6f);

        Vector turned = DashMovement.headingAt(north, 16);
        assertEquals(0.0f, DashAnimation.echoPitch(turned), 1e-6f,
                "a horizontal launch stays level, so its echoes never pitch");
        assertTrue(Math.abs(DashAnimation.echoYaw(turned)) > 1.0f,
                "echo yaw must turn with the path, not stay at the cast heading");
    }

    @Test
    void pitchedHeadingProducesNonzeroEchoPitch() {
        // headingAt preserves pitch, so a nonzero-pitch launch must pitch its echoes too.
        Vector h = DashMovement.headingAt(new Vector(0, -1, 1), 0);
        assertEquals(45.0f, DashAnimation.echoPitch(h), 1e-4f,
                "a nonzero-pitch heading must produce a nonzero echo pitch");
    }

    @Test
    void blockedGroundStartRequiresDownwardForwardAndNoAirborneHistory() {
        assertTrue(DashAnimation.blockedGroundStart(-0.4, true, false));
        assertFalse(DashAnimation.blockedGroundStart(0.0, true, false));
        assertFalse(DashAnimation.blockedGroundStart(0.4, true, false));
        assertFalse(DashAnimation.blockedGroundStart(-0.4, true, true));
        assertFalse(DashAnimation.blockedGroundStart(-0.4, false, false));
    }

    @Test
    void swingIsOnlyOnTickZeroAndNoLaunchFrameRequestsShake() {
        List<Integer> swings = DashAnimation.launch().stream()
                .filter(DashAnimation.Frame::swing).map(DashAnimation.Frame::tick).toList();
        assertTrue(DashAnimation.launch().stream().noneMatch(DashAnimation.Frame::shake),
                "no launch frame may request a velocity shake");
        assertEquals(List.of(0), swings);
    }

    @Test
    void ghostScaleShrinksMonotonicallyToItsFloor() {
        assertEquals(10, DashAnimation.GHOST_LIFE);
        assertEquals(1.0f, DashAnimation.ghostScale(0), 1.0e-6f);
        for (int age = 1; age <= DashAnimation.GHOST_LIFE; age++) {
            assertTrue(DashAnimation.ghostScale(age) < DashAnimation.ghostScale(age - 1),
                    "scale must shrink at age " + age);
        }
        assertEquals(0.05f, DashAnimation.ghostScale(DashAnimation.GHOST_LIFE), 1.0e-6f);
        assertEquals(0.05f, DashAnimation.ghostScale(DashAnimation.GHOST_LIFE + 10), 1.0e-6f);
    }
}
