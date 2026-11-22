package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure slot arithmetic shared by every screen — no Bukkit runtime required. */
class GuiLayoutTest {

    @Test
    void slotRoundTripsThroughColumnAndRow() {
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                int raw = GuiLayout.slot(col, row);
                assertEquals(col, GuiLayout.col(raw));
                assertEquals(row, GuiLayout.row(raw));
            }
        }
    }

    @Test
    void inBoundsRejectsOutsideTheScreen() {
        assertTrue(GuiLayout.inBounds(0, 0, 3));
        assertTrue(GuiLayout.inBounds(8, 2, 3));
        assertFalse(GuiLayout.inBounds(9, 0, 6));
        assertFalse(GuiLayout.inBounds(0, 3, 3));
        assertFalse(GuiLayout.inBounds(-1, 0, 6));
    }
}
