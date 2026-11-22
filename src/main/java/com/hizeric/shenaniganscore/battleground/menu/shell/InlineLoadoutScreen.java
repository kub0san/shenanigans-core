package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.loadout.GlobalKit;
import com.hizeric.shenaniganscore.battleground.loadout.LoadoutCodec;
import com.hizeric.shenaniganscore.battleground.loadout.LoadoutSwap;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiCard;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiConfirm;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiDock;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiGuard;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiPager;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiScreen;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSounds;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiTheme;
import com.hizeric.shenaniganscore.battleground.menu.base.SafeText;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.shop.ShopCatalog;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.hizeric.shenaniganscore.battleground.menu.base.screen.ScreenRegistry;

/**
 * Single loadout screen: horizontal tabs switch the grid between Abilities,
 * Ultimate and Items. The kit itself is edited in the player's real inventory.
 */
public final class InlineLoadoutScreen extends GuiScreen {
    private enum Mode { ABILITIES, ULTIMATE, ITEMS }

    private static final String SEED_KEY = "loadout.seeded";
    private static final int PAGE_SIZE = 8;
    private static final int[] CARD_COLS = {1, 3, 5, 7};
    private static final int[] TAB_COLS = {2, 4, 6};
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ShenanigansCore plugin;
    private Mode mode = Mode.ABILITIES;
    private int page;
    /** Computed once per redraw; both content and chrome need the eligible list. */
    private List<AbilityDefinition> eligibleCache = List.of();

    public InlineLoadoutScreen(ShenanigansCore plugin, Player player) {
        super(GuiSessions.currentOr(plugin), player, ScreenRegistry.LOADOUT);
        this.plugin = plugin;
        seedOnce(player);
    }

    private BattlegroundPlayer data() {
        return plugin.getBattlegroundManager().playerStore().get(player.getUniqueId());
    }

    // ------------------------------------------------------------------ real inventory kit

    @SuppressWarnings("unchecked")
    private void seedOnce(Player p) {
        Map<String, Object> values = session.stagedStore()
                .computeIfAbsent(p.getUniqueId(), id -> new java.util.LinkedHashMap<>());
        if (values.containsKey(SEED_KEY)) return;
        BattlegroundPlayer data = data();
        plugin.getBattlegroundManager().inventories().captureOnce(p.getUniqueId(),
                p.getInventory().getContents(), p.getItemOnCursor());
        p.setItemOnCursor(null);
        applyBoard(p, LoadoutCodec.decode(data == null ? "" : data.layoutData(), GlobalKit.kit()).items());
        values.put(SEED_KEY, Boolean.TRUE);
    }

    private static ItemStack[] liveBoard(Player p) {
        ItemStack[] contents = p.getInventory().getContents();
        ItemStack[] board = new ItemStack[GlobalKit.BOARD_SIZE];
        for (int i = 0; i < board.length; i++) board[i] = i < contents.length ? contents[i] : null;
        return LoadoutSwap.snapshot(board);
    }

    private static ItemStack[] applyBoard(Player p, ItemStack[] board) {
        for (int i = 0; i < GlobalKit.BOARD_SIZE; i++) {
            ItemStack item = i < board.length ? board[i] : null;
            p.getInventory().setItem(i, item == null ? null : item.clone());
        }
        p.updateInventory();
        return board;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void protect(ChestGui chest) {
        GuiGuard.readOnly(chest, spec.permission(), rows());
    }

    @Override
    protected void renderContent(StaticPane content) {
        eligibleCache = mode == Mode.ABILITIES ? eligible() : List.of();
        Mode[] modes = Mode.values();
        String[] names = {"ABILITIES", "ULTIMATE", "ITEMS"};
        Material[] icons = {Material.BLAZE_ROD, Material.BARRIER, Material.CHEST};
        String[] models = {"ability_catalog", "ultimate_locked", null};
        for (int i = 0; i < modes.length; i++) {
            final Mode target = modes[i];
            org.bukkit.inventory.ItemStack tab = GuiTheme.named(icons[i],
                    (mode == target ? GuiTheme.accent() : GuiTheme.MUTED) + "<bold>", names[i]);
            if (models[i] != null) GuiTheme.model(tab, models[i]);
            put(content, TAB_COLS[i], 1, tab,
                    (p, e) -> { mode = target; page = 0; GuiSounds.click(p); refresh(); });
        }

        switch (mode) {
            case ABILITIES -> renderAbilities(content);
            case ULTIMATE -> renderUltimate(content);
            case ITEMS -> renderItems(content);
        }
    }

    private void renderAbilities(StaticPane content) {
        List<AbilityDefinition> options = eligibleCache;
        GuiPager pager = GuiPager.of(page, options.size(), PAGE_SIZE);
        page = pager.currentPage();
        List<AbilityDefinition> slice = pager.slice(options);
        if (slice.isEmpty()) {
            put(content, 4, 2, GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "EMPTY",
                    GuiTheme.dim("Buy an ability in the market first")), (p, e) -> { });
            return;
        }
        for (int i = 0; i < slice.size(); i++) {
            AbilityDefinition definition = slice.get(i);
            String name = PlainTextComponentSerializer.plainText().serialize(definition.displayName());
            boolean bound = data() != null && definition.id().equals(data().rail().get(AbilityRail.NORMAL_SLOT));
            final AbilityId id = definition.id();
            ItemStack card = GuiCard.card(definition.iconMaterial(), name, null,
                    bound ? GuiCard.CardState.READY : GuiCard.CardState.EMPTY);
            String model = GuiTheme.abilityModel(id.toString());            if (model != null) GuiTheme.model(card, model);
            put(content, CARD_COLS[i % CARD_COLS.length], 2 + i / CARD_COLS.length,
                    card,
                    (p, e) -> bind(p, id));
        }
    }

    private void renderUltimate(StaticPane content) {
        put(content, 4, 2, GuiTheme.named(Material.BARRIER, GuiTheme.RED + "<bold>", "EMPTY",
                GuiTheme.dim("No ultimate techniques exist yet"),
                GuiTheme.dim("Slot 3 is reserved, but nothing can be bound.")), (p, e) -> { });
    }

    private void renderItems(StaticPane content) {
        BattlegroundPlayer data = data();
        ShopCatalog catalog = plugin.getBattlegroundManager().shopCatalog();
        int index = 0;
        for (ShopEntry entry : catalog.entries()) {
            if (!entry.isCustomItem() || data == null || !data.owns(entry.id())) continue;
            if (index >= PAGE_SIZE) break;
            final ShopEntry owned = entry;
            put(content, CARD_COLS[index % CARD_COLS.length], 2 + index / CARD_COLS.length,
                    GuiTheme.tool(owned.icon(), SafeText.escape(owned.displayName()),
                            "Replace the sword in slot 1"),
                    (p, e) -> equip(p, catalog, owned));
            index++;
        }
        if (index == 0) {
            put(content, 4, 2, GuiTheme.named(Material.BARRIER, GuiTheme.MUTED + "<bold>", "EMPTY",
                    GuiTheme.dim("Buy an item in the market first")), (p, e) -> { });
        }
    }

    @Override
    protected void renderChrome(StaticPane chrome) {
        GuiDock dock = GuiDock.of(this)
                .action(6, GuiTheme.tool(Material.BARRIER, "BACK",
                                "Save your kit and return to the hub"),
                        (p, e) -> save(p))
                .action(7, GuiTheme.dangerTool(Material.TNT, "RESET", "Restore the server kit"),
                        (p, e) -> confirmReset(p));
        if (mode == Mode.ABILITIES) {
            dock.pager(GuiPager.of(page, eligibleCache.size(), PAGE_SIZE),
                    target -> { page = target; refresh(); });
        }
        dock.applyTo(chrome);
    }


    // ------------------------------------------------------------------ actions

    private void equip(Player p, ShopCatalog catalog, ShopEntry rendered) {
        // Re-resolve and re-check at click time: the rendered card may be stale if an admin
        // disabled or revoked the entry after this screen was drawn.
        ShopEntry entry = catalog.entry(rendered.id());
        BattlegroundPlayer owner = data();
        if (entry == null || !entry.enabled() || owner == null || !owner.owns(entry.id())) {
            GuiSounds.fail(p);
            notice(p, GuiTheme.fail("That item is no longer available."));
            return;
        }
        ItemStack custom = catalog.itemStack(entry);
        if (custom == null || custom.getType().isAir()) {
            GuiSounds.fail(p);
            notice(p, GuiTheme.fail("That item cannot be equipped."));
            return;
        }
        ItemStack[] next = LoadoutSwap.equipSword(liveBoard(p), custom.clone());
        p.getInventory().setItem(LoadoutSwap.SWORD_SLOT, next[LoadoutSwap.SWORD_SLOT]);
        GuiSounds.success(p);
        notice(p, GuiTheme.ok("Equipped " + SafeText.escape(entry.displayName()) + "."));
        refresh();
    }

    private void bind(Player p, AbilityId id) {
        BattlegroundPlayer current = data();
        var entry = plugin.getBattlegroundManager().shopCatalog().entryForAbility(id.toString());
        if (current == null || entry == null || !current.owns(entry.id())) return;
        current.rail().set(AbilityRail.NORMAL_SLOT, id);
        plugin.getBattlegroundManager().playerStore().markDirty(p.getUniqueId());
        GuiSounds.success(p);
        refresh();
    }

    private void save(Player p) {
        BattlegroundPlayer current = data();
        if (current == null) {
            GuiSounds.fail(p);
            notice(p, GuiTheme.fail("No battleground profile yet — deploy once first."));
            return;
        }
        p.setItemOnCursor(null);
        current.setLayoutData(LoadoutCodec.encode(liveBoard(p)).data());
        plugin.getBattlegroundManager().playerStore().markDirty(p.getUniqueId());
        Map<String, Object> values = session.stagedStore().get(p.getUniqueId());
        if (values != null) values.remove(SEED_KEY);
        GuiSounds.success(p);
        new HubScreen(plugin, p).show();
    }

    private void confirmReset(Player p) {
        GuiConfirm.open(p, "Reset your kit layout?", new String[]{
                        "Your real inventory returns to the server kit.",
                        "The reset is saved immediately."}, true, spec.permission(),
                () -> {
                    BattlegroundPlayer current = data();
                    if (current == null) return;
                    current.setLayoutData(LoadoutCodec.encode(applyBoard(p,
                            LoadoutSwap.reset(GlobalKit.kit()))).data());
                    plugin.getBattlegroundManager().playerStore().markDirty(p.getUniqueId());
                    Map<String, Object> values = session.stagedStore().get(p.getUniqueId());
                    if (values != null) values.put(SEED_KEY, Boolean.TRUE);
                    GuiSounds.success(p);
                    show();
                },
                this::show);
    }

    private List<AbilityDefinition> eligible() {
        List<AbilityDefinition> result = new ArrayList<>();
        BattlegroundPlayer data = data();
        if (data == null) return result;
        var catalog = plugin.getBattlegroundManager().shopCatalog();
        for (AbilityDefinition definition : plugin.getAbilityRegistry().all()) {
            if (definition.hidden()) continue;
            ShopEntry entry = catalog.entryForAbility(definition.id().toString());
            if (entry == null || !entry.isAbility() || entry.isUltimate() || !data.owns(entry.id())) continue;
            result.add(definition);
        }
        return result;
    }


    private static void notice(Player p, String miniMessage) {
        p.sendActionBar(MM.deserialize(miniMessage));
    }
}
