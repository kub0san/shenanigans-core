package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.loadout.GlobalKit;
import com.hizeric.shenaniganscore.battleground.loadout.LoadoutCodec;
import com.hizeric.shenaniganscore.battleground.loadout.LoadoutSwap;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiCard;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiChatPrompt;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiDock;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiPager;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSounds;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiTheme;
import com.hizeric.shenaniganscore.battleground.menu.base.SafeText;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.shop.MarketView;
import com.hizeric.shenaniganscore.battleground.shop.ShopCatalog;
import com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/** Market: category tabs, a grid of cards, and live ownership/streak state. */
public final class MarketScreen extends GuiScreen {
    private static final int PER_PAGE = 8;
    private static final int[] CARD_COLS = {1, 3, 5, 7};
    private static final int[] TAB_COLS = {1, 3, 5, 7};
    private static final ShopEntry.Filter[] FILTERS = {
            ShopEntry.Filter.ALL, ShopEntry.Filter.CUSTOM_ITEMS,
            ShopEntry.Filter.ABILITIES, ShopEntry.Filter.ULTIMATES
    };

    private final ShenanigansCore plugin;
    private ShopEntry.Filter filter = ShopEntry.Filter.ALL;
    private int page;
    private GuiPager currentPager = GuiPager.of(0, 0, PER_PAGE);

    public MarketScreen(ShenanigansCore plugin, Player player) {
        super(com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions.currentOr(plugin),
                player, ScreenRegistry.MARKET);
        this.plugin = plugin;
    }

    @Override
    protected void renderContent(StaticPane content) {
        for (int i = 0; i < FILTERS.length; i++) {
            ShopEntry.Filter value = FILTERS[i];
            final int col = TAB_COLS[i];
            int count = ShopEntry.filter(catalog().entries(), value).size();
            put(content, col, 1,
                    GuiCard.filterTab(tabIcon(value), label(value), filter == value, count),
                    (p, e) -> { filter = value; page = 0; refresh(); });
        }

        BattlegroundPlayer data = plugin.getBattlegroundManager().playerStore().get(player.getUniqueId());
        int streak = data == null ? 0 : data.streak();
        MarketView.Page view = MarketView.pageAndPager(catalog().entries(), filter, page, PER_PAGE,
                streak, this::owns, ShopPolicyConfig.current().killstreakRequirements());
        currentPager = view.pager();
        page = currentPager.currentPage();
        List<MarketView.Row> rows = view.rows();

        if (rows.isEmpty()) {
            put(content, 4, 2, GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "EMPTY",
                    GuiTheme.dim("No entries in this category")), (p, e) -> { });
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            MarketView.Row row = rows.get(i);
            put(content, CARD_COLS[i % CARD_COLS.length], 2 + i / CARD_COLS.length, card(row),
                    (p, e) -> buy(row));
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        BattlegroundPlayer data = plugin.getBattlegroundManager().playerStore().get(player.getUniqueId());
        GuiDock.of(this)
                .back((p, e) -> new HubScreen(plugin, p).show())
                .pager(currentPager, target -> { page = target; refresh(); })
                .action(7, GuiTheme.named(Material.GOLD_INGOT, GuiTheme.accent() + "<bold>", "CREDITS",
                        GuiTheme.value("balance", String.valueOf(data == null ? 0 : data.credits()))),
                        (p, e) -> { })
                .applyTo(chrome);
    }


    private static Material tabIcon(ShopEntry.Filter filter) {
        return switch (filter) {
            case ALL -> Material.CHEST;
            case ABILITIES -> Material.ENCHANTED_BOOK;
            case CUSTOM_ITEMS -> Material.IRON_SWORD;
            case ULTIMATES -> Material.NETHER_STAR;
        };
    }


    private ItemStack card(MarketView.Row row) {
        ShopEntry entry = row.entry();
        String color = row.usable() ? GuiTheme.GREEN : (row.owned() ? GuiTheme.GOLD : GuiTheme.RED);
        String state = row.usable() ? "Usable" : (row.owned() ? "Streak locked" : "Locked");
        String name = SafeText.escape(entry.displayName());
        ItemStack icon = GuiTheme.named(entry.icon(), color + "<bold>", name,
                GuiTheme.value("Price", row.price() + " credits"),
                GuiTheme.status(color, row.usable(), state),
                row.streakRequired() > 0
                        ? GuiTheme.value("Killstreak", row.streakProgress() + "/" + row.streakRequired())
                        : null);
        String model = entry.isCustomItem() ? null : GuiTheme.abilityModel(entry.abilityId());
        if (model != null) GuiTheme.model(icon, model);
        return icon;
    }

    private boolean owns(ShopEntry entry) {
        BattlegroundPlayer data = plugin.getBattlegroundManager().playerStore().get(player.getUniqueId());
        return data != null && data.owns(entry.id());
    }

    private ShopEntry fresh(String id) {
        for (ShopEntry entry : catalog().entries()) {
            if (entry.id().equals(id)) return entry;
        }
        return null;
    }

    private void buy(MarketView.Row rendered) {
        ShopEntry entry = fresh(rendered.entry().id());
        if (entry == null || !entry.enabled()) {
            fail("That entry is no longer available.");
            return;
        }
        if (entry.isUltimate()) {
            fail("Ultimates are not available.");
            return;
        }
        BattlegroundPlayer data = plugin.getBattlegroundManager().playerStore().get(player.getUniqueId());
        if (data == null) {
            fail("No battleground profile yet.");
            return;
        }
        if (data.owns(entry.id())) {
            GuiSounds.fail(player);
            GuiChatPrompt.say(player, GuiTheme.warn("You already own that entry."));
            return;
        }

        ItemStack toGrant = entry.isCustomItem() ? catalog().itemStack(entry) : null;
        if (entry.isCustomItem() && toGrant == null) {
            fail("That entry is misconfigured — notify an admin.");
            return;
        }

        int price = Math.max(0, entry.price());
        BattlegroundPlayer.PurchaseResult result =
                plugin.getBattlegroundManager().economy().purchaseCatalog(player, entry.id(), price);
        if (result != BattlegroundPlayer.PurchaseResult.SUCCESS) {
            GuiSounds.fail(player);
            switch (result) {
                case ALREADY_OWNED -> GuiChatPrompt.say(player, GuiTheme.warn("You already own that entry."));
                case INVALID_ENTRY -> GuiChatPrompt.say(player, GuiTheme.fail("That entry is no longer available."));
                default -> GuiChatPrompt.say(player, GuiTheme.fail("Not enough credits."));
            }
            return;
        }

        if (MarketDelivery.requiresInventoryMutation(entry.isCustomItem(), toGrant != null, false, result)) {
            fail("Staged purchases must not mutate inventory.");
            return;
        }

        if (toGrant != null) {
            // Stage the purchased item in layout data so it survives the next deploy.
            LoadoutCodec.DecodeResult dec = LoadoutCodec.decode(data.layoutData(), GlobalKit.kit());
            data.setLayoutData(LoadoutCodec.encode(
                    LoadoutSwap.equipSword(dec.items(), toGrant.clone())).data());
        }
        plugin.getBattlegroundManager().playerStore().markDirty(player.getUniqueId());
        GuiSounds.success(player);
        GuiChatPrompt.say(player, GuiTheme.ok("Purchased " + SafeText.escape(entry.displayName()) + "."));
        refresh();
    }

    private void fail(String message) {
        GuiSounds.fail(player);
        GuiChatPrompt.say(player, GuiTheme.fail(message));
    }

    private static String label(ShopEntry.Filter filter) {
        return switch (filter) {
            case ALL -> "ALL";
            case CUSTOM_ITEMS -> "BOUGHT ITEMS";
            case ABILITIES -> "ABILITIES";
            case ULTIMATES -> "ULTIMATES";
        };
    }

    private ShopCatalog catalog() {
        return plugin.getBattlegroundManager().shopCatalog();
    }
}
