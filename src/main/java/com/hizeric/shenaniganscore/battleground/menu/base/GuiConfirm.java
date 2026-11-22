package com.hizeric.shenaniganscore.battleground.menu.base;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenSpec;

/**
 * Three-row confirmation: TNT in the middle, Confirm on the left, Cancel on the right.
 * By default Confirm is green and Cancel red; a destructive action passes {@code danger = true}
 * and the two are swapped so the dangerous choice is highlighted red.
 */
public final class GuiConfirm {

    private GuiConfirm() {
    }

    /** Which choice gets the danger styling, derived from the call-site's intent. */
    public record Styles(boolean confirmDanger, boolean cancelDanger) { }

    /** Pure seam: a destructive action highlights Confirm instead of Cancel. */
    public static Styles styles(boolean danger) {
        return new Styles(danger, !danger);
    }


    /**
     * @param details    short lines explaining what will happen (may be empty)
     * @param danger    true when Confirm performs a destructive action — swap the danger colouring
     * @param permission checked on every click, or null for none
     */
    public static void open(Player player, String title, String[] details, boolean danger, String permission,
                            Runnable onConfirm, Runnable onCancel) {
        new Screen(GuiSessions.currentOr(JavaPlugin.getProvidingPlugin(GuiConfirm.class)),
                player, title, details, danger, permission, onConfirm, onCancel).show();
    }

    private static final class Screen extends GuiScreen {
        private final String heading;
        private final String[] details;
        private final boolean danger;
        private final Runnable onConfirm;
        private final Runnable onCancel;

        private Screen(GuiSession session, Player player, String heading, String[] details, boolean danger,
                       String permission, Runnable onConfirm, Runnable onCancel) {
            super(session, player, new ScreenSpec("confirm-dynamic", "<white>CONFIRM",
                    GuiLayout.ROWS_CONFIRM, GuiFrame.Density.NONE, permission));
            this.heading = heading;
            this.details = details == null ? new String[0] : details;
            this.danger = danger;
            this.onConfirm = onConfirm;
            this.onCancel = onCancel;
        }

        @Override
        protected String title() {
            return heading;
        }

        @Override
        protected void renderContent(StaticPane content) {
            String[] lore = new String[details.length];
            for (int i = 0; i < details.length; i++) lore[i] = GuiTheme.dim(details[i]);
            content.addItem(wrap(GuiTheme.named(Material.TNT, GuiTheme.GOLD + "<bold>", "CONFIRM?",
                    lore), (p, e) -> { }, player), 4, 1);

            Styles styles = styles(danger);
            ItemStack confirm = styles.confirmDanger()
                    ? GuiTheme.dangerTool(Material.RED_DYE, "CONFIRM", "Apply this action")
                    : GuiTheme.tool(Material.LIME_DYE, "CONFIRM", "Apply this action");
            ItemStack cancel = styles.cancelDanger()
                    ? GuiTheme.dangerTool(Material.RED_DYE, "CANCEL", "Go back")
                    : GuiTheme.tool(Material.LIME_DYE, "CANCEL", "Go back");

            content.addItem(wrap(confirm, (p, e) -> {
                        GuiSounds.click(p);
                        p.closeInventory();
                        if (onConfirm != null) onConfirm.run();
                    }, player), 2, 1);

            content.addItem(wrap(cancel, (p, e) -> {
                        GuiSounds.click(p);
                        p.closeInventory();
                        if (onCancel != null) onCancel.run();
                    }, player), 6, 1);
        }

        @Override
        protected void renderChrome(StaticPane chrome) {
            // The two choices are the only actions. Permission is enforced once by
            // GuiScreen.show() for opening and once by wrap()/REQUIRED for clicking.
        }
    }
}
