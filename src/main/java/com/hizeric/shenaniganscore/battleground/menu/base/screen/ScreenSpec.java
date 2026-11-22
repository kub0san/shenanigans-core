package com.hizeric.shenaniganscore.battleground.menu.base.screen;

import com.hizeric.shenaniganscore.battleground.menu.base.GuiFrame;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiLayout;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;

/**
 * The immutable contract for one screen: identity, MiniMessage title, chest rows,
 * frame density and the permission needed to open it. Screen specs are validated at
 * construction so a broken row count fails loudly instead of producing a silent
 * mis-rendered GUI. Pure data — no Bukkit runtime.
 */
public record ScreenSpec(String id, String title, int rows, GuiFrame.Density density, String permission) {

    public static final int MIN_ROWS = 3;
    public static final int MAX_ROWS = 6;

    public ScreenSpec {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("ScreenSpec id must not be blank");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("ScreenSpec title must not be blank");
        }
        if (rows < MIN_ROWS || rows > MAX_ROWS) {
            throw new IllegalArgumentException("ScreenSpec rows must be " + MIN_ROWS + "-" + MAX_ROWS + ": " + rows);
        }
        if (density == null) {
            throw new IllegalArgumentException("ScreenSpec density must not be null");
        }
        if (permission != null && permission.isBlank()) {
            throw new IllegalArgumentException("ScreenSpec permission must be null or non-blank");
        }
    }

    /** The last row is always the action/dock row, regardless of screen height. */
    public int dockRow() {
        return rows - 1;
    }

    /** Total slots in the chest for this spec. */
    public int size() {
        return rows * GuiLayout.COLUMNS;
    }
}