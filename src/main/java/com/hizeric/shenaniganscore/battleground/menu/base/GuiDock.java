package com.hizeric.shenaniganscore.battleground.menu.base;

import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * The dock: the bottom row of a screen, painted over the bottom glass frame.
 *
 * <pre>
 *  0      1  2   3     4      5     6  7  8
 * [Back] [ ] [ ] [◀]  [Page]  [▶]  [ ] [ ] [action]
 * </pre>
 */
public final class GuiDock {
    public static final int COL_BACK = 0;
    public static final int COL_PREV = 3;
    public static final int COL_PAGE = 4;
    public static final int COL_NEXT = 5;
    private static final Set<Integer> PAGER_COLUMNS = Set.of(COL_PREV, COL_PAGE, COL_NEXT);

    private final GuiScreen screen;
    private final int row;
    private GuiClick backClick;
    private GuiPager pager;
    private IntConsumer goTo;
    private final Map<Integer, GuiItem> actions = new LinkedHashMap<>();

    private GuiDock(GuiScreen screen) {
        this.screen = screen;
        this.row = screen.dockRow();
    }

    public static GuiDock of(GuiScreen screen) {
        return new GuiDock(screen);
    }

    public GuiDock back(GuiClick click) {
        if (actions.containsKey(COL_BACK)) {
            throw new IllegalStateException("GuiDock back collides with action at column " + COL_BACK);
        }
        this.backClick = click;
        return this;
    }

    public GuiDock pager(GuiPager pager, IntConsumer goToPage) {
        // Symmetric with action(): applying a pager after an action must not silently
        // overwrite that action when applyTo() paints the pager columns.
        for (int col : PAGER_COLUMNS) {
            if (actions.containsKey(col)) {
                throw new IllegalStateException("GuiDock pager collides with action at column " + col);
            }
        }
        this.pager = pager;
        this.goTo = goToPage;
        return this;
    }

    /** A custom button in dock column {@code col} (0-8). Columns 0 and 3-5 are reserved. */
    public GuiDock action(int col, ItemStack item, GuiClick click) {
        if (col < 0 || col >= GuiLayout.COLUMNS) {
            throw new IllegalArgumentException("Dock column out of bounds: " + col);
        }
        if (col == COL_BACK || (pager != null && PAGER_COLUMNS.contains(col))) {
            throw new IllegalStateException("GuiDock action at column " + col + " collides with back/pager");
        }
        actions.put(col, GuiScreen.wrap(item, click, screen.player()));
        return this;
    }

    public void applyTo(StaticPane chrome) {

        if (backClick != null) {
            chrome.addItem(GuiScreen.wrap(GuiTheme.back(), (p, e) -> {
                GuiSounds.click(p);
                backClick.on(p, e);
            }, screen.player()), COL_BACK, row);
        }

        if (pager != null && goTo != null && pager.maxPages() > 1) {
            final int page = pager.currentPage();
            final int max = pager.maxPages();

            if (page > 0) {
                chrome.addItem(GuiScreen.wrap(GuiTheme.arrow(false),
                        (p, e) -> go(p, page - 1), screen.player()), COL_PREV, row);
            }
            chrome.addItem(GuiScreen.wrap(GuiTheme.pageItem(page, max), (p, e) -> {
                // MenuListener cancels shift-clicks before InventoryFramework dispatches them,
                // so only plain left/right clicks can reach this handler.
                int target = e.getClick() == org.bukkit.event.inventory.ClickType.RIGHT
                        ? (page - 1 + max) % max
                        : (page + 1) % max;
                go(p, target);
            }, screen.player()), COL_PAGE, row);
            if (page < max - 1) {
                chrome.addItem(GuiScreen.wrap(GuiTheme.arrow(true),
                        (p, e) -> go(p, page + 1), screen.player()), COL_NEXT, row);
            }
        }

        actions.forEach((col, item) -> chrome.addItem(item, col, row));
    }

    private void go(org.bukkit.entity.Player p, int target) {
        GuiSounds.click(p);
        goTo.accept(target);
    }
}
