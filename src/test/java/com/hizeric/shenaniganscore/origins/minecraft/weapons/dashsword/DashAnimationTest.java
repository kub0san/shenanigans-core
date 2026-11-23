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

        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(2));
        assertTrue(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(20));
        assertFalse(DashAnimation.beat(DashAnimation.Kind.GHOST_ARC).fires(21));
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
    void soundsFireAtZeroTenAndTwenty() {
        List<Integer> ticks = DashAnimation.launch().stream()
                .filter(frame -> !frame.sounds().isEmpty())
                .map(DashAnimation.Frame::tick)
                .toList();
        assertEquals(List.of(0, 10, 20), ticks);
    }

    @Test
    void swingAndShakeAreOnTheExpectedTicks() {
        List<Integer> swings = DashAnimation.launch().stream()
                .filter(DashAnimation.Frame::swing).map(DashAnimation.Frame::tick).toList();
        List<Integer> shakes = DashAnimation.launch().stream()
                .filter(DashAnimation.Frame::shake).map(DashAnimation.Frame::tick).toList();
        assertEquals(List.of(0), swings);
        assertEquals(List.of(3, 6), shakes);
    }

    @Test
    void ghostScaleShrinksWithAge() {
        assertEquals(1.0f, DashAnimation.ghostScale(0), 1.0e-6f);
        assertTrue(DashAnimation.ghostScale(DashAnimation.GHOST_LIFE) < 0.1f);
    }
}
