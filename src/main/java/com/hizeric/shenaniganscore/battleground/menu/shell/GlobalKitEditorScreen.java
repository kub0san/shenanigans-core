package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import com.hizeric.shenaniganscore.battleground.loadout.GlobalKitService;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiChatPrompt;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiConfirm;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSounds;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiTheme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

import java.util.logging.Level;

/**
 * Admin loadout editor. The admin arranges their real inventory, then SAVE LOADOUT
 * copies it into global-kit.yml. There is no editable board, no draft and no drag
 * handling — which removes the entire class of capture/refresh/handler bugs the
 * old chest editor had.
 */
public final class GlobalKitEditorScreen extends GuiScreen {
    private final ShenanigansCore plugin;

    public GlobalKitEditorScreen(ShenanigansCore plugin, Player player) {
        super(com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions.currentOr(plugin),
                player, ScreenRegistry.GLOBAL_KIT);
        this.plugin = plugin;
    }

    @Override
    protected void renderContent(StaticPane content) {
        put(content, 1, 1, GuiTheme.tool(Material.LIME_DYE, "SAVE LOADOUT",
                "Copy your inventory into the server kit"), (p, e) -> save(p));
        put(content, 3, 1, GuiTheme.tool(Material.OAK_SIGN, "RELOAD",
                "Re-read the saved loadout file"), (p, e) -> confirmReload(p));
        put(content, 5, 1, GuiTheme.dangerTool(Material.TNT, "RESET",
                "Restore the built-in default kit"), (p, e) -> confirmReset(p));
        put(content, 7, 1, GuiTheme.tool(Material.ARROW, "BACK",
                "Return to the admin panel"), (p, e) -> new AdminScreen(plugin, p).show());
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // All actions live in the single content row.
    }


    private GlobalKitService service() {
        return plugin.globalKitService();
    }

    private void save(Player p) {
        ItemStack[] board = GlobalKitService.importFrom(p);
        if (!service().save(board).ok()) {
            GuiSounds.fail(p);
            GuiChatPrompt.say(p, GuiTheme.fail("Could not write the loadout file."));
            return;
        }
        GuiSounds.success(p);
        GuiChatPrompt.say(p, GuiTheme.ok("Server loadout saved."));
        refresh();
    }

    private void confirmReload(Player p) {
        GuiConfirm.open(p, "Reload the server loadout?", new String[]{
                        "The saved file is read again.",
                        "Any unsaved kit in this file is discarded."}, false, Permissions.KIT_ADMIN,
                () -> {
                    try {
                        service().reload();
                        GuiSounds.success(p);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Could not reload the server loadout", e);
                        GuiSounds.fail(p);
                        GuiChatPrompt.say(p, GuiTheme.fail("Reload failed — check console."));
                    }
                    new GlobalKitEditorScreen(plugin, p).show();
                },
                () -> new GlobalKitEditorScreen(plugin, p).show());
    }

    private void confirmReset(Player p) {
        GuiConfirm.open(p, "Reset to the default loadout?", new String[]{
                        "The saved override file is deleted.",
                        "The built-in kit becomes authoritative."}, true, Permissions.KIT_ADMIN,
                () -> {
                    try {
                        service().reset();
                        GuiSounds.success(p);
                    } catch (RuntimeException e) {
                        plugin.getLogger().log(Level.WARNING, "Could not reset the server loadout", e);
                        GuiSounds.fail(p);
                        GuiChatPrompt.say(p, GuiTheme.fail("Reset failed — check console."));
                    }
                    new GlobalKitEditorScreen(plugin, p).show();
                },
                () -> new GlobalKitEditorScreen(plugin, p).show());
    }
}
