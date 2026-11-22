package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiDock;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSounds;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiTheme;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** Admin root: live round summary and two destinations. */
public final class AdminScreen extends GuiScreen {
    private final ShenanigansCore plugin;

    public AdminScreen(ShenanigansCore plugin, Player player) {
        super(com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions.currentOr(plugin),
                player, ScreenRegistry.ADMIN);
        this.plugin = plugin;
    }

    @Override
    protected void renderContent(StaticPane content) {
        var manager = plugin.getBattlegroundManager();

        put(content, 2, 1, GuiTheme.named(Material.NAME_TAG, GuiTheme.accent() + "<bold>", "ROUND",
                GuiTheme.value("number", String.valueOf(manager.roundNumber())),
                GuiTheme.value("state", String.valueOf(manager.state()))), (p, e) -> { });
        put(content, 4, 1, GuiTheme.named(Material.CLOCK, GuiTheme.GOLD + "<bold>", "CLOCK",
                GuiTheme.value("remaining", manager.remainingSeconds() + "s")), (p, e) -> { });
        put(content, 6, 1, GuiTheme.named(Material.PLAYER_HEAD, GuiTheme.MUTED + "<bold>", "PLAYERS",
                GuiTheme.value("deployed", String.valueOf(manager.deployedCount()))), (p, e) -> { });

        put(content, 3, 2, GuiTheme.named(Material.CLOCK, GuiTheme.TEXT + "<bold>", "ROUNDS",
                GuiTheme.dim("Start, stop, skip and end rounds")),
                (p, e) -> new RoundControlsScreen(plugin, p).show());
        put(content, 5, 2, GuiTheme.named(Material.CHEST, GuiTheme.TEXT + "<bold>", "LOADOUT",
                GuiTheme.dim("Set the server kit from your inventory")),
                (p, e) -> new GlobalKitEditorScreen(plugin, p).show());
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            GuiSounds.click(p);
            new HubScreen(plugin, p).show();
        }, player), GuiDock.COL_BACK, dockRow());
    }

}
