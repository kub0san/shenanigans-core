package com.hizeric.shenaniganscore.battleground.menu.base;

import java.util.HashSet;
import java.util.Set;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** Startup invariants for the spec-driven layouts. Pure logic — unit-tested. */
public final class GuiLayoutChecks {

    private GuiLayoutChecks() {
    }

    public static void verify() {
        checkSpecs();
        checkConfirm();
        checkDock();
    }

    private static void checkSpecs() {
        Set<String> ids = new HashSet<>();
        for (ScreenSpec spec : ScreenRegistry.ALL) {
            if (!ids.add(spec.id())) {
                throw new IllegalStateException("Duplicate ScreenSpec id: " + spec.id());
            }
            for (int col = 0; col < GuiLayout.COLUMNS; col++) {
                if (!GuiLayout.inBounds(col, spec.dockRow(), spec.rows())) {
                    throw new IllegalStateException(spec.id() + " has an out-of-bounds dock row");
                }
            }
            if (spec.size() != spec.rows() * GuiLayout.COLUMNS) {
                throw new IllegalStateException(spec.id() + " has an invalid chest size");
            }
        }
    }

    private static void checkConfirm() {
        if (GuiLayout.ROWS_CONFIRM != ScreenRegistry.CONFIRM.rows()) {
            throw new IllegalStateException("Confirmation spec and row constant disagree");
        }
    }

    private static void checkDock() {
        if (GuiDock.COL_PAGE != (GuiDock.COL_PREV + GuiDock.COL_NEXT) / 2) {
            throw new IllegalStateException("GuiDock pager columns are not centred");
        }
        if (GuiDock.COL_BACK == GuiDock.COL_PREV || GuiDock.COL_BACK == GuiDock.COL_PAGE) {
            throw new IllegalStateException("GuiDock back collides with the pager");
        }
    }
}
