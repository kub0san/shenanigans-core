package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.Test;

import com.hizeric.shenaniganscore.battleground.menu.base.GuiFrame;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Screen specs carry the row-size contract; invalid specs must fail loudly at startup. */
class ScreenSpecTest {

    private static ScreenSpec spec(int rows) {
        return new ScreenSpec("test", "<white>Test", rows, GuiFrame.Density.BORDER, "perm.test");
    }

    @Test
    void dockRowIsTheLastRow() {
        for (int rows = ScreenSpec.MIN_ROWS; rows <= ScreenSpec.MAX_ROWS; rows++) {
            assertEquals(rows - 1, spec(rows).dockRow());
        }
    }

    @Test
    void sizeIsRowsTimesNine() {
        assertEquals(27, spec(3).size());
        assertEquals(54, spec(6).size());
    }

    @Test
    void rejectsRowsOutsideChestRange() {
        assertThrows(IllegalArgumentException.class, () -> spec(2));
        assertThrows(IllegalArgumentException.class, () -> spec(7));
    }

    @Test
    void rejectsBlankIdentityAndNullDensity() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenSpec("", "<white>T", 3, GuiFrame.Density.NONE, "p"));
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenSpec("t", "<white>T", 3, GuiFrame.Density.NONE, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenSpec("t", "<white>T", 3, null, "p"));
    }
}
