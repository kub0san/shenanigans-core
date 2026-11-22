package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.admin.AdminRoundGate;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiChatPrompt;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiDock;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSounds;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiTheme;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** Round controls: status, four round actions and an exact-time prompt. */
public final class RoundControlsScreen extends GuiScreen {
    private final ShenanigansCore plugin;
    private String lastResult = "None";

    public RoundControlsScreen(ShenanigansCore plugin, Player player) {
        super(com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions.currentOr(plugin),
                player, ScreenRegistry.ROUND_CONTROLS);
        this.plugin = plugin;
    }

    @Override
    protected void renderContent(StaticPane content) {
        var manager = plugin.getBattlegroundManager();
        put(content, 2, 1, GuiTheme.named(Material.NAME_TAG, GuiTheme.accent() + "<bold>", "ROUND",
                GuiTheme.value("number", String.valueOf(manager.roundNumber())),
                GuiTheme.value("state", String.valueOf(manager.state()))), (p, e) -> { });
        put(content, 4, 1, GuiTheme.named(Material.CLOCK, GuiTheme.GOLD + "<bold>", "CLOCK",
                GuiTheme.value("remaining", clock((int) manager.remainingSeconds()))), (p, e) -> askExact());
        put(content, 6, 1, GuiTheme.named(Material.PLAYER_HEAD, GuiTheme.MUTED + "<bold>", "PLAYERS",
                GuiTheme.value("deployed", String.valueOf(manager.deployedCount()))), (p, e) -> { });

        put(content, 1, 2, GuiTheme.tool(Material.LIME_DYE, "START", "Start a round now"),
                (p, e) -> act(p, AdminRoundGate.Action.START));
        put(content, 3, 2, GuiTheme.tool(Material.RED_DYE, "STOP", "Stop and clear players"),
                (p, e) -> act(p, AdminRoundGate.Action.STOP));
        put(content, 5, 2, GuiTheme.named(Material.SPECTRAL_ARROW, GuiTheme.TEXT + "<bold>", "SKIP",
                GuiTheme.dim("Skip the remaining time")),
                (p, e) -> act(p, AdminRoundGate.Action.SKIP));
        put(content, 7, 2, GuiTheme.dangerTool(Material.BARRIER, "END", "Pull players back and regenerate"),
                (p, e) -> act(p, AdminRoundGate.Action.END));

        put(content, 3, 3, GuiTheme.tool(Material.OAK_SIGN, "SET TIME", "Enter an exact remaining time"),
                (p, e) -> askExact());
        put(content, 5, 3, GuiTheme.named(Material.PAPER, GuiTheme.MUTED + "<bold>", "LAST ACTION",
                GuiTheme.value("result", lastResult)), (p, e) -> { });
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        chrome.addItem(wrap(GuiTheme.back(), (p, e) -> {
            GuiSounds.click(p);
            new AdminScreen(plugin, p).show();
        }, player), GuiDock.COL_BACK, dockRow());
    }


    private void act(Player actor, AdminRoundGate.Action action) {
        AdminRoundGate.Result result = plugin.getBattlegroundManager().adminRoundAction(actor, action, 0);
        lastResult = (result.allowed() ? "OK " : "Denied ") + action;
        if (result.allowed()) {
            GuiSounds.success(player);
        } else {
            GuiSounds.fail(player);
            player.sendActionBar(MiniMessage.miniMessage().deserialize(GuiTheme.fail(result.message())));
        }
        refresh();
    }

    private void askExact() {
        // The prompt closes this GUI; suppress the staged-hub reopen until the callback.
        MenuLatch.get().suppress(player);
        int max = ArenaRuntimeConfig.current().roundDurationSeconds();
        GuiChatPrompt.ask(plugin, player, "Remaining seconds (0-" + max + ")", answer -> {
            // The prompt runs up to a minute after the permission-gated click. Re-authorize
            // here as well as inside adminRoundAction so a demotion cannot set the clock.
            if (!player.isOnline() || !player.hasPermission(Permissions.BG_ADMIN)) {
                MenuLatch.get().release(player);
                return;
            }
            try {
                int seconds = Integer.parseInt(answer.trim());
                if (seconds < 0 || seconds > max) {
                    lastResult = "Out of range";
                    GuiSounds.fail(player);
                    GuiChatPrompt.say(player, GuiTheme.fail("Enter a value between 0 and "
                            + max + "."));
                } else {
                    AdminRoundGate.Result result = plugin.getBattlegroundManager()
                            .adminRoundAction(player, AdminRoundGate.Action.SET_TIME, seconds);
                    lastResult = (result.allowed() ? "OK time set" : "Denied time");
                    if (result.allowed()) GuiSounds.success(player);
                    else {
                        GuiSounds.fail(player);
                        player.sendActionBar(MiniMessage.miniMessage()
                                .deserialize(GuiTheme.fail(result.message())));
                    }
                }
            } catch (NumberFormatException ignored) {
                lastResult = "Invalid number";
                GuiSounds.fail(player);
                GuiChatPrompt.say(player, GuiTheme.fail("That is not a number — nothing changed."));
            }
            MenuLatch.get().release(player);
            if (player.isOnline() && !com.hizeric.shenaniganscore.battleground.menu.MenuManager.get().isOpen(player)) {
                show();
            }
        }, () -> {
            MenuLatch.get().release(player);
            if (player.isOnline() && !com.hizeric.shenaniganscore.battleground.menu.MenuManager.get().isOpen(player)) {
                show();
            }
        });
    }

    private static String clock(int seconds) {
        int safe = Math.max(0, seconds);
        return (safe / 60) + ":" + String.format("%02d", safe % 60);
    }
}
