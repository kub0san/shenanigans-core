package com.hizeric.shenaniganscore.battleground.menu.base;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.inventory.ItemStack;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;

/**
 * Paints the decorative background layer. Every screen gets a full navy canvas so
 * the vanilla grey chest texture never shows through — buttons live on the content
 * pane above it. Decoration never shares a priority with content.
 */
public final class GuiFrame {
    public enum Density {
        BORDER, DOCK, EDITOR_ARMOR, NONE
    }

    private GuiFrame() {
    }

    public static void paint(StaticPane background, ScreenSpec spec) {
        paint(background, spec.rows());
    }

    public static void paint(StaticPane background, int rows) {
        for (int r = 0; r < rows; r++) {
            row(background, r, GuiTheme.filler());
        }
    }

    public static void row(StaticPane background, int row) {
        row(background, row, GuiTheme.filler());
    }

    public static void row(StaticPane background, int row, ItemStack glass) {
        row(background, row, 0, GuiLayout.COLUMNS, glass);
    }

    public static void row(StaticPane background, int row, int fromCol, int toColExclusive, ItemStack glass) {
        if (row < 0 || glass == null) return;
        for (int col = Math.max(0, fromCol); col < toColExclusive && col < GuiLayout.COLUMNS; col++) {
            // filler() returns a fresh ItemStack per paint; GuiItem never mutates it, so
            // sharing one instance across the row avoids 9 clones per row.
            background.addItem(new GuiItem(glass), col, row);
        }
    }
}
