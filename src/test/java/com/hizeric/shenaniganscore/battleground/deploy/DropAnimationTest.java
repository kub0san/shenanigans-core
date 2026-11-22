package com.hizeric.shenaniganscore.battleground.deploy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Starfall Lock frames are pure geometry, so their cues can be checked without a server. */
class DropAnimationTest {

    @Test
    void lockOnExpandsARingAndShowsTheTargetText() {
        DropAnimation.Frame first = DropAnimation.lockOn(0, 30, 0, 0);
        DropAnimation.Frame last = DropAnimation.lockOn(0, 30, 0, DropAnimation.LOCK_TICKS - 2);

        assertFalse(first.beats().isEmpty());
        assertEquals(2, first.sounds().size());
        assertTrue(first.displays().isEmpty());
        assertEquals(DropAnimation.DisplayKind.LOCK_TEXT, last.displays().get(0).kind());
        assertTrue(last.displays().get(0).text().contains("TARGET LOCKED"));
        assertTrue(last.displays().get(0).lifeTicks() > 0);
    }

    @Test
    void descentHelixAndWarpRingEmitOnSchedule() {
        DropAnimation.Frame zero = DropAnimation.descent(0, 40, 0, 0);
        DropAnimation.Frame offBeat = DropAnimation.descent(0, 40, 0, 1);

        assertEquals(3, offBeat.beats().size(), "three helixes emit on an ordinary tick");
        assertTrue(zero.beats().size() > offBeat.beats().size(),
                "tick zero also adds the warp ring");
        assertEquals("ENTITY_ENDER_DRAGON_FLAP", zero.sounds().get(0).key());
        assertEquals(DropAnimation.DisplayKind.AFTERIMAGE, zero.displays().get(0).kind());
        assertTrue(DropAnimation.descent(0, 40, 0, 4).beats().size() == zero.beats().size(),
                "every fourth tick adds the same 12-beat warp ring");
    }

    @Test
    void impactFiresOneShotCuesAndTheImpactText() {
        DropAnimation.Frame first = DropAnimation.impact(0, 30, 0, 0);
        DropAnimation.Frame textFrame = DropAnimation.impact(0, 30, 0, 6);

        assertEquals(3, first.sounds().size());
        assertTrue(first.beats().stream().anyMatch(b -> b.kind() == DropAnimation.Kind.EXPLOSION));
        assertTrue(first.beats().stream().anyMatch(b -> b.kind() == DropAnimation.Kind.SONIC_BOOM));
        assertTrue(first.beats().stream().anyMatch(b -> b.kind() == DropAnimation.Kind.FLASH));
        assertTrue(textFrame.displays().stream()
                .anyMatch(d -> d.kind() == DropAnimation.DisplayKind.IMPACT_TEXT));
    }

    @Test
    void allFramesRemainInsideTheirExpectedCounts() {
        for (int tick = 0; tick < DropAnimation.LOCK_TICKS; tick++) {
            assertFalse(DropAnimation.lockOn(1, 2, 3, tick).beats().isEmpty());
        }
        for (int tick = 0; tick < DropAnimation.IMPACT_TICKS; tick++) {
            assertFalse(DropAnimation.impact(1, 2, 3, tick).beats().isEmpty());
        }
    }
}
