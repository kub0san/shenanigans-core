package com.hizeric.shenaniganscore.battleground.menu.shell;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A close caused by opening another GUI must not schedule another hub open —
 * otherwise every screen switch reopens the hub and the close fires again.
 */
class MenuLatchTest {
    private static final UUID PLAYER = UUID.randomUUID();

    @Test
    void stagedPlayerWithNoTransitionReopens() {
        MenuLatch.get().clear(PLAYER);
        assertTrue(MenuLatch.get().shouldReopen(PLAYER, true));
        assertFalse(MenuLatch.get().shouldReopen(PLAYER, false));
    }

    @Test
    void openingAScreenSuppressesTheCloseItCauses() {
        MenuLatch.get().clear(PLAYER);
        MenuLatch.get().suppress(PLAYER);
        assertFalse(MenuLatch.get().shouldReopen(PLAYER, true));
        MenuLatch.get().release(PLAYER);
        assertTrue(MenuLatch.get().shouldReopen(PLAYER, true));
    }

    @Test
    void nestedScreenOpensStaySuppressedUntilAllRelease() {
        MenuLatch.get().clear(PLAYER);
        MenuLatch.get().suppress(PLAYER); // transition A opens a GUI
        MenuLatch.get().suppress(PLAYER); // its close schedules B, which opens another GUI
        MenuLatch.get().release(PLAYER);  // A's release must not unsuppress B
        assertFalse(MenuLatch.get().shouldReopen(PLAYER, true));
        MenuLatch.get().release(PLAYER);
        assertTrue(MenuLatch.get().shouldReopen(PLAYER, true));
    }
}
