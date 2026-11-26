package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.RoundState;
import com.hizeric.shenaniganscore.battleground.command.AbilityCommand;
import com.hizeric.shenaniganscore.battleground.command.BattlegroundCommand;
import com.hizeric.shenaniganscore.battleground.command.CommandHelp;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import com.hizeric.shenaniganscore.battleground.menu.MenuManager;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiDock;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSounds;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiStats;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiTheme;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Arrays;
import java.util.List;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** Hub: one statistics dossier, three destinations and a live round readout. */
public final class HubScreen extends GuiScreen {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ShenanigansCore plugin;

    public HubScreen(ShenanigansCore plugin, Player player) {
        super(com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions.currentOr(plugin),
                player, ScreenRegistry.HUB);
        this.plugin = plugin;
    }

    @Override
    protected void renderContent(StaticPane content) {
        var manager = plugin.getBattlegroundManager();
        BattlegroundPlayer data = manager.playerStore().get(player.getUniqueId());
        boolean restoring = manager.state() == RoundState.RESTORING;
        int inside = manager.deployedCount();
        String insideLine = inside + (inside == 1 ? " player" : " players") + " inside";

        put(content, 4, 1, statisticsItem(data), (p, e) -> { });

        put(content, 2, 2, GuiTheme.named(Material.EMERALD, GuiTheme.accent() + "<bold>", "MARKET",
                GuiTheme.dim("Spend credits on items and abilities")),
                (p, e) -> new MarketScreen(plugin, p).show());
        put(content, 4, 2, restoring
                        ? GuiTheme.named(Material.BARRIER, GuiTheme.RED + "<bold>", "RESTORING",
                        GuiTheme.dim("Deploy unlocks shortly"))
                        : GuiTheme.named(Material.ELYTRA, GuiTheme.accent() + "<bold>", "DEPLOY",
                        GuiTheme.dim(insideLine)),
                (p, e) -> {
                    if (manager.state() == RoundState.RESTORING) return;
                    // Suppress explicitly: closeInventory() fires onClose synchronously, and
                    // without a latch token the staged-hub reopen depends on join() flipping
                    // staged before the reopen task runs. Hold the token across the whole
                    // sequence and release it next tick so no reopen can be scheduled.
                    MenuLatch.get().suppress(p);
                    try {
                        p.closeInventory();
                        MenuManager.get().clearIfOpen(p);
                        manager.join(p);
                    } finally {
                        Bukkit.getScheduler().runTask(plugin,
                                () -> MenuLatch.get().release(p));
                    }
                });
        put(content, 6, 2, GuiTheme.named(Material.ENCHANTED_BOOK, GuiTheme.accent() + "<bold>", "LOADOUT",
                GuiTheme.dim("Bind abilities and arrange your kit")),
                (p, e) -> new InlineLoadoutScreen(plugin, p).show());

        if (player.hasPermission(Permissions.BG_ADMIN)) {
            put(content, 3, 3, GuiTheme.named(Material.REDSTONE_BLOCK, GuiTheme.accent() + "<bold>", "ADMIN",
                    GuiTheme.dim("Round and loadout controls")),
                    (p, e) -> new AdminScreen(plugin, p).show());
            put(content, 5, 3, GuiTheme.named(Material.CLOCK, GuiTheme.accent() + "<bold>", "ROUND",
                    GuiTheme.value("state", String.valueOf(manager.state())),
                    GuiTheme.value("players", String.valueOf(inside))), (p, e) -> { });
        } else {
            put(content, 4, 3, GuiTheme.named(Material.CLOCK, GuiTheme.accent() + "<bold>", "ROUND",
                    GuiTheme.value("state", String.valueOf(manager.state())),
                    GuiTheme.value("players", String.valueOf(inside))), (p, e) -> { });
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        // Same reply shape as /bg help: the full two-root overview, sent line by line.
        put(chrome, 7, dockRow(), GuiTheme.named(Material.PAPER, GuiTheme.accent() + "<bold>", "HELP",
                GuiTheme.dim("List every command")),
                (p, e) -> {
                    GuiSounds.click(p);
                    for (String line : CommandHelp.render(List.of(
                            new BattlegroundCommand(plugin).root(),
                            new AbilityCommand(plugin).root()), null)) {
                        p.sendMessage(MM.deserialize(line));
                    }
                });
        GuiDock.of(this)
                .action(8, GuiTheme.plain(Material.BARRIER, GuiTheme.RED, "LEAVE"), (p, e) -> {
                    GuiSounds.click(p);
                    p.closeInventory();
                    MenuManager.get().clearIfOpen(p);
                    plugin.getBattlegroundManager().leave(p);
                })
                .applyTo(chrome);
    }


    private ItemStack statisticsItem(BattlegroundPlayer data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = head.getItemMeta();
        if (meta == null) return head;
        if (meta instanceof SkullMeta skull) skull.setOwningPlayer(player);
        meta.displayName(MM.deserialize("<!italic>" + GuiTheme.accent() + "<bold>STATISTICS"));
        GuiStats.Snapshot stats = new GuiStats.Snapshot(player.getName(),
                data == null ? 0 : data.kills(), data == null ? 0 : data.deaths(),
                data == null ? 0 : data.credits(), data == null ? 0 : data.bestStreak());
        meta.lore(Arrays.stream(GuiStats.lore(stats))
                .map(line -> MM.deserialize("<!italic>" + line))
                .toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        head.setItemMeta(meta);
        return head;
    }
}
