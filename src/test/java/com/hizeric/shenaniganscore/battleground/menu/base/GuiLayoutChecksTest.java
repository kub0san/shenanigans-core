package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** The same invariants the plugin checks at startup, locked down as pure unit tests. */
class GuiLayoutChecksTest {

    @Test
    void shippedScreenSpecsPassStartupValidation() {
        assertDoesNotThrow(GuiLayoutChecks::verify);
    }

    /** Every screen keeps a purpose-fit row count; the editor and confirm are the smallest. */
    @Test
    void rowCountsMatchTheInstrumentPanelLayouts() {
        assertEquals(5, ScreenRegistry.HUB.rows());
        assertEquals(5, ScreenRegistry.LOADOUT.rows());
        assertEquals(5, ScreenRegistry.ADMIN.rows());
        assertEquals(5, ScreenRegistry.ROUND_CONTROLS.rows());
        assertEquals(5, ScreenRegistry.MARKET.rows());
        assertEquals(3, ScreenRegistry.GLOBAL_KIT.rows());
        assertEquals(3, ScreenRegistry.CONFIRM.rows());
    }
}
