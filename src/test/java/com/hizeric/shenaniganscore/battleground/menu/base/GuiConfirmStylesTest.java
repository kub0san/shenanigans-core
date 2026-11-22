package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuiConfirmStylesTest {

    @Test
    void aNormalConfirmIsGreenAndCancelIsRed() {
        GuiConfirm.Styles styles = GuiConfirm.styles(false);

        assertFalse(styles.confirmDanger(), "a non-destructive confirm must not be highlighted red");
        assertTrue(styles.cancelDanger(), "cancel stays the red, non-destructive choice");
    }

    @Test
    void aDestructiveConfirmIsRedAndCancelIsGreen() {
        GuiConfirm.Styles styles = GuiConfirm.styles(true);

        assertTrue(styles.confirmDanger(), "the destructive action must be highlighted red");
        assertFalse(styles.cancelDanger(), "cancel must not look more dangerous than confirm");
    }
}
