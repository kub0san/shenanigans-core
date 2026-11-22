package com.hizeric.shenaniganscore.battleground.menu.base;

import com.github.stefvanschie.inventoryframework.adventuresupport.ComponentHolder;
import com.github.stefvanschie.inventoryframework.gui.GuiItem;
import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.Pane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Slot;
import com.hizeric.shenaniganscore.battleground.menu.MenuManager;
import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/**
 * Base class for every battleground screen.
 *
 * <p>New framework: two panes. The background pane (LOW) only holds decoration from
 * {@link GuiFrame}; the content pane (NORMAL) holds every button, including the dock.
 * Chrome is rendered into the content pane after content so it paints on top. There
 * is no reflection and no third "high priority" pane to fight with.</p>
 *
 * <p>Every screen is described by a {@link ScreenSpec} — its rows, frame, title and
 * permission live in one validated place ({@link ScreenRegistry}).</p>
 */
public abstract class GuiScreen {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    /**
     * Permission required by the screen each player currently has open. Cancelling a
     * click does not stop InventoryFramework from firing button handlers, so authorization
     * is enforced here at the dispatch seam instead of in GuiGuard.
     */
    private static final Map<Inventory, String> REQUIRED = new ConcurrentHashMap<>();

    protected final GuiSession session;
    protected final Player player;
    protected final ScreenSpec spec;

    /** kept for screens that index raw slots directly; equals {@code spec.rows()}. */
    protected final int rows;

    protected final StaticPane backgroundPane;
    protected final StaticPane contentPane;

    private ChestGui chest;
    /** Coalesces same-tick refresh requests into one redraw. */
    private final AtomicBoolean refreshScheduled = new AtomicBoolean();

    protected GuiScreen(GuiSession session, Player player, ScreenSpec spec) {
        this.session = session;
        this.player = player;
        this.spec = spec;
        this.rows = spec.rows();
        this.backgroundPane = new StaticPane(GuiLayout.COLUMNS, spec.rows(), Pane.Priority.LOW);
        this.contentPane = new StaticPane(GuiLayout.COLUMNS, spec.rows(), Pane.Priority.NORMAL);
    }

    public final GuiSession session() {
        return session;
    }

    public final Player player() {
        return player;
    }

    public final ScreenSpec spec() {
        return spec;
    }

    public final int rows() {
        return spec.rows();
    }

    public final GuiFrame.Density density() {
        return spec.density();
    }

    /** The dock row: always the last row. */
    public final int dockRow() {
        return spec.dockRow();
    }

    // ---- contracts for subclasses ----

    /** Defaults to the spec title; dynamic screens (market filter) may override. */
    protected String title() {
        return spec.title();
    }

    protected abstract void renderContent(StaticPane content);

    /** Drawn into the content pane after content, so dock buttons sit on top. */
    protected abstract void renderChrome(StaticPane chrome);

    /** Decoration only. Override for a different frame material (the market grid). */
    protected void renderFrame(StaticPane background) {
        GuiFrame.paint(background, spec);
    }

    /** Called before {@code chest.show(player)}. */
    protected void configure(ChestGui chest) {
    }

    /** Runs on close, after the shared open-flag cleanup. */
    protected void onClose(InventoryCloseEvent event) {
    }

    /** Screens that write items straight into the inventory opt in explicitly. */
    protected boolean paintsInventory() {
        return false;
    }

    /** Runs after the initial show and after every refresh when {@link #paintsInventory()} is true. */
    protected void afterInventory(Inventory inventory, boolean refresh) {
    }

    /** Click protection. Editors override this with an editable-slot guard. */
    protected void protect(ChestGui chest) {
        GuiGuard.readOnly(chest, spec.permission(), rows());
    }

    protected final ChestGui chest() {
        return chest;
    }

    // ---- lifecycle ----

    public final void show() {
        if (spec.permission() != null && !player.hasPermission(spec.permission())) {
            MenuManager.get().clearIfOpen(player);
            return;
        }
        // Opening a GUI closes the previous one and fires InventoryCloseEvent. Hold a
        // latch token across that close so MenuListener does not schedule another hub.
        MenuLatch.get().suppress(player);
        try {
            showScreen();
        } finally {
            Bukkit.getScheduler().runTask(session.plugin(), () -> MenuLatch.get().release(player));
        }
    }

    private void showScreen() {
        session.flushOpenEditor(player);
        chest = new ChestGui(rows(), ComponentHolder.of(titleComponent()));
        protect(chest);

        chest.addPane(Slot.fromXY(0, 0), backgroundPane);
        chest.addPane(Slot.fromXY(0, 0), contentPane);

        redraw();
        configure(chest);
        final ChestGui screenChest = chest;
        screenChest.setOnClose(event -> {
            REQUIRED.remove(screenChest.getInventory());
            MenuManager.get().clearIfOpen(player);
            onClose(event);
        });
        screenChest.show(player);
        // Both marks go AFTER show(): opening B synchronously fires A's close handler, which
        // clears its own inventory-keyed entry and the open marker. Registering first would
        // silently drop B's permission check (GUI-3) and leave B unmarked.
        if (spec.permission() == null) REQUIRED.remove(screenChest.getInventory());
        else REQUIRED.put(screenChest.getInventory(), spec.permission());
        MenuManager.get().markIfOpen(player, chest.getInventory());
        if (paintsInventory()) afterInventory(chest.getInventory(), false);
    }

    public final void refresh() {
        if (!refreshScheduled.compareAndSet(false, true)) return;
        try {
            Bukkit.getScheduler().runTask(session.plugin(), () -> {
                // Release before redrawing so a refresh raised during redraw schedules a new task.
                refreshScheduled.set(false);
                if (chest == null) return;
                if (player.getOpenInventory().getTopInventory() != chest.getInventory()) return;
                redraw();
                chest.update();
                if (paintsInventory()) afterInventory(chest.getInventory(), true);
                // The title is a full Component from the initial show. Re-serializing it to
                // legacy codes on refresh flattened the theme's hex colours, so never do that.
            });
        } catch (RuntimeException e) {
            refreshScheduled.set(false);
            throw e;
        }
    }

    private void redraw() {
        backgroundPane.clear();
        contentPane.clear();
        renderFrame(backgroundPane);
        renderContent(contentPane);
        renderChrome(contentPane);
    }

    /** Titles are plain names; the accent tag is applied at open time so config reloads land live. */
    private Component titleComponent() {
        return MM.deserialize(GuiTheme.accent() + "<bold>" + MM.escapeTags(title()));
    }

    /** Wraps an item + click into a GuiItem, resolving the clicking player. */
    public static GuiItem wrap(ItemStack item, GuiClick click, Player fallback) {
        return new GuiItem(item, event -> {
            Player clicker = event.getWhoClicked() instanceof Player p ? p : fallback;
            if (clicker == null) return;
            // Fail closed: a button whose chest has no registered requirement is never runnable.
            String needed = REQUIRED.get(event.getView().getTopInventory());
            if (needed == null || !clicker.hasPermission(needed)) return;
            click.on(clicker, event);
        });
    }

    /** Convenience: places a wrapped button into a pane. Screens may override nothing. */
    protected final void put(StaticPane pane, int col, int row, ItemStack item, GuiClick click) {
        pane.addItem(wrap(item, click, player), col, row);
    }

    /** Drops all cached per-player permission requirements on shutdown/reload (F-50). */
    public static void clearRequired() {
        REQUIRED.clear();
    }
}
