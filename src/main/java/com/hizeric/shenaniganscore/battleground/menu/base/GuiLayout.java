package com.hizeric.shenaniganscore.battleground.menu.base;

/** Pure slot arithmetic shared by every screen. No Bukkit runtime. */
public final class GuiLayout {

    private GuiLayout() {
    }

    /** Total columns in a chest. */
    public static final int COLUMNS = 9;

    /** Confirmation dialogs are the smallest screens: TNT in the middle row. */
    public static final int ROWS_CONFIRM = 3;

    /** Converts a column/row pair into a raw chest slot. */
    public static int slot(int col, int row) {
        return row * COLUMNS + col;
    }

    public static int col(int rawSlot) {
        return rawSlot % COLUMNS;
    }

    public static int row(int rawSlot) {
        return rawSlot / COLUMNS;
    }

    /** True when a column/row pair fits inside a chest with the given number of rows. */
    public static boolean inBounds(int col, int row, int rows) {
        return col >= 0 && col < COLUMNS && row >= 0 && row < rows;
    }
}