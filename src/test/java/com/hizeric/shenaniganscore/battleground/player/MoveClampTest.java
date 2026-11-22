package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M6: clamp a staged player by rewriting the move, not by teleporting on every move packet. */
class MoveClampTest {

    @Test
    void onlyPositionsBeyondTheBubbleAreClamped() {
        Location anchor = new Location(null, 10, 64, 20);

        assertFalse(MoveClamp.outside(new Location(null, 11, 64, 20), anchor, 2.25));
        assertTrue(MoveClamp.outside(new Location(null, 12, 64, 20), anchor, 2.25));
    }

    @Test
    void snappingMovesPositionButKeepsTheLookDirection() {
        Location anchor = new Location(null, 10, 64, 20);
        Location target = new Location(null, 30, 90, 40, 45f, -10f);

        Location snapped = MoveClamp.snap(target, anchor);

        assertEquals(10, snapped.getBlockX());
        assertEquals(64, snapped.getBlockY());
        assertEquals(20, snapped.getBlockZ());
        assertEquals(45f, snapped.getYaw());
        assertEquals(-10f, snapped.getPitch());
        assertEquals(30, target.getBlockX(), "the original move must not be mutated");
    }
}
