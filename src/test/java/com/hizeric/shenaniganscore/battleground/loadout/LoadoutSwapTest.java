package com.hizeric.shenaniganscore.battleground.loadout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Ticket 08: the pure loadout-swap seam. String stand-ins are used for slots because a real
 * Bukkit ItemStack cannot be constructed without a running Paper registry in a unit test.
 */
class LoadoutSwapTest {

    private static String[] kitBoard() {
        String[] board = new String[GlobalKit.BOARD_SIZE];
        for (int i = 0; i < board.length; i++) {
            board[i] = "slot-" + i;
        }
        return board;
    }

    @Test
    void equippingACustomSwordReplacesOnlySlotOne() {
        String[] current = kitBoard();

        String[] equipped = LoadoutSwap.equipSword(current, "custom-sword");

        assertEquals("custom-sword", equipped[LoadoutSwap.SWORD_SLOT]);
        for (int i = 0; i < current.length; i++) {
            if (i == LoadoutSwap.SWORD_SLOT) continue;
            assertSame(current[i], equipped[i], "slot " + i + " must not change");
        }
    }

    @Test
    void equippingNothingClearsTheSwordSlot() {
        String[] current = kitBoard();

        String[] equipped = LoadoutSwap.equipSword(current, null);

        org.junit.jupiter.api.Assertions.assertNull(equipped[LoadoutSwap.SWORD_SLOT]);
        for (int i = 1; i < current.length; i++) {
            assertSame(current[i], equipped[i], "slot " + i + " must not change");
        }
    }

    @Test
    void resettingRestoresEverySlotFromTheServerKit() {
        String[] kit = kitBoard();
        String[] rearranged = new String[GlobalKit.BOARD_SIZE];
        rearranged[0] = "custom-sword";
        rearranged[9] = "moved";

        String[] restored = LoadoutSwap.reset(kit);

        for (int i = 0; i < kit.length; i++) {
            assertEquals(kit[i], restored[i], "slot " + i + " must come from the server kit");
        }
    }

    @Test
    void savingSnapshotsTheLiveLayoutRatherThanAliasingIt() {
        String[] live = kitBoard();

        String[] saved = LoadoutSwap.snapshot(live);
        live[0] = "changed-after-save";
        live[9] = "changed-after-save";

        assertEquals("slot-0", saved[0]);
        assertEquals("slot-9", saved[9]);
    }
}
