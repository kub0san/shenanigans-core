package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.RoundState;
import com.hizeric.shenaniganscore.battleground.economy.EconomyService;
import com.hizeric.shenaniganscore.battleground.indicator.ActionBarView;
import com.hizeric.shenaniganscore.battleground.indicator.IndicatorIcon;
import com.hizeric.shenaniganscore.battleground.indicator.TechniqueGlyphs;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;

/**
 * The battleground HUD: one action-bar line of three slots — held custom-item ability,
 * normal ability, ultimate. With the resource pack each slot is one flat atlas glyph
 * (ready, locked, or a cooldown fill level); plain marks are the no-pack fallback.
 *
 * <p>Cooldown is sampled once per slot: the same millisecond reading feeds both the displayed
 * seconds and the glyph fill level, so the two can never disagree.</p>
 */
public final class TechniqueHud {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final ActionBarNotice NOTICES = new ActionBarNotice();
    private static final int FILL_LEVELS = 20;

    private static final IndicatorIcon ITEM_ICON = IndicatorIcon.of("<gold>◆");
    private static final IndicatorIcon ABILITY_ICON = IndicatorIcon.of("<aqua>◆");
    private static final IndicatorIcon ULTIMATE_ICON = IndicatorIcon.of("<light_purple>◆");
    private static final IndicatorIcon EMPTY_MARK = IndicatorIcon.of("—");

    private record Cooldown(int seconds, long remainingMillis) { }

    private TechniqueHud() { }

    public static void send(ShenanigansCore plugin, Player player) {
        BattlegroundManager manager = plugin.getBattlegroundManager();
        if (!manager.isDeployed(player.getUniqueId())) return;
        if (manager.staging().isStaged(player)) return;
        RoundState state = manager.state();
        if (state == RoundState.STAGING || state == RoundState.RESTORING || state == RoundState.IDLE) return;
        if (com.hizeric.shenaniganscore.battleground.menu.MenuManager.get().isOpen(player)) return;
        if (plugin.getSessionManager().hasActiveSession(player.getUniqueId())) return;

        BattlegroundPlayer data = manager.playerStore().get(player.getUniqueId());
        if (data == null) return;

        ActionBarView.SlotSource held = heldSource(plugin, player, data);
        ActionBarView.SlotSource normal = boundSource(plugin, player, data,
                data.rail().get(AbilityRail.NORMAL_SLOT), ABILITY_ICON);
        ActionBarView.SlotSource ultimate = boundSource(plugin, player, data,
                data.rail().get(AbilityRail.ULTIMATE_SLOT), ULTIMATE_ICON);

        plugin.indicatorRouter().send(player,
                ActionBarView.render(ActionBarView.build(held, normal, ultimate)));
    }

    /** Slot 1 follows the held custom item; a plain item leaves it hidden. */
    private static ActionBarView.SlotSource heldSource(ShenanigansCore plugin, Player player,
                                                       BattlegroundPlayer data) {
        BattlegroundManager manager = plugin.getBattlegroundManager();
        ItemStack held = manager.heldCustomItem(player);
        ShopEntry entry = manager.shopCatalog().customItemFor(held);
        AbilityId heldAbility = manager.heldItemAbility(player, held);

        HeldItemAbility.SyncResult sync = HeldItemAbility.sync(data.rail(), heldAbility);
        if (sync.changed()) manager.playerStore().markDirty(player.getUniqueId());

        if (entry == null || heldAbility == null) {
            return ActionBarView.SlotSource.none(emptyIcon(EMPTY_MARK, packEnabled(plugin)));
        }
        ShopEntry.Gate gate = entry.gate(true, data.streak());
        Cooldown cooldown = cooldown(plugin, player, heldAbility);
        return new ActionBarView.SlotSource(heldAbility, gate, cooldown.seconds(),
                glyphIcon(heldAbility, gate, cooldown.remainingMillis(), ITEM_ICON,
                        packEnabled(plugin), plugin, player),
                entry.price(), entry.displayName());
    }

    private static ActionBarView.SlotSource boundSource(ShenanigansCore plugin, Player player,
                                                        BattlegroundPlayer data, AbilityId id,
                                                        IndicatorIcon icon) {
        if (id == null) return ActionBarView.SlotSource.none(emptyIcon(EMPTY_MARK, packEnabled(plugin)));
        BattlegroundManager manager = plugin.getBattlegroundManager();
        ShopEntry entry = manager.shopCatalog().entryForAbility(id.toString());
        EconomyService.CastGate cast = manager.economy().castGate(player, id, manager.shopCatalog());
        Cooldown cooldown = cooldown(plugin, player, id);
        return new ActionBarView.SlotSource(id, cast.display(entry), cooldown.seconds(),
                glyphIcon(id, cast, cooldown.remainingMillis(), icon, packEnabled(plugin), plugin, player),
                data.credits(), entry == null ? null : entry.displayName());
    }

    /** One millis read per slot; seconds are derived with ceil so 1 ms still shows "1". */
    private static Cooldown cooldown(ShenanigansCore plugin, Player player, AbilityId id) {
        long remaining = plugin.getCooldownManager()
                .getRemainingMillis(player.getUniqueId(), id.toString());
        return new Cooldown((int) Math.ceil(remaining / 1000.0), remaining);
    }

    private static boolean packEnabled(ShenanigansCore plugin) {
        return plugin.resourcePackManager() != null && plugin.resourcePackManager().enabled();
    }

    private static IndicatorIcon emptyIcon(IndicatorIcon fallback, boolean allowPack) {
        if (!allowPack) return fallback;
        return IndicatorIcon.glyph(TechniqueGlyphs.EMPTY);
    }

    private static IndicatorIcon glyphIcon(AbilityId id, EconomyService.CastGate gate,
                                           long remainingMillis, IndicatorIcon fallback,
                                           boolean allowPack, ShenanigansCore plugin, Player player) {
        TechniqueGlyphs.State state = !gate.owned() || !gate.streakMet()
                ? TechniqueGlyphs.State.LOCKED
                : (remainingMillis > 0 ? TechniqueGlyphs.State.COOLDOWN : TechniqueGlyphs.State.READY);
        return glyphIcon(id, state, remainingMillis, fallback, allowPack, plugin, player);
    }

    private static IndicatorIcon glyphIcon(AbilityId id, ShopEntry.Gate gate,
                                           long remainingMillis, IndicatorIcon fallback,
                                           boolean allowPack, ShenanigansCore plugin, Player player) {
        TechniqueGlyphs.State state = !gate.canUse()
                ? TechniqueGlyphs.State.LOCKED
                : (remainingMillis > 0 ? TechniqueGlyphs.State.COOLDOWN : TechniqueGlyphs.State.READY);
        return glyphIcon(id, state, remainingMillis, fallback, allowPack, plugin, player);
    }

    private static IndicatorIcon glyphIcon(AbilityId id, TechniqueGlyphs.State state,
                                           long remainingMillis, IndicatorIcon fallback,
                                           boolean allowPack, ShenanigansCore plugin, Player player) {
        if (!allowPack || id == null) return fallback;
        TechniqueGlyphs.Technique tech = TechniqueGlyphs.byId(id.toString());
        if (tech == null) return fallback;
        // The one millis reading sampled in cooldown() feeds the fill level too.
        int level = fillLevel(plugin, player, id, state, remainingMillis);
        return IndicatorIcon.glyph(tech.glyph(state, level));
    }

    /** 0 = empty cooldown bar, FILL_LEVELS = full. */
    private static int fillLevel(ShenanigansCore plugin, Player player, AbilityId id,
                                 TechniqueGlyphs.State state, long remainingMillis) {
        if (state == TechniqueGlyphs.State.READY) return FILL_LEVELS;
        if (state == TechniqueGlyphs.State.LOCKED) return 0;
        int total = plugin.getCooldownManager().getTotalSeconds(player.getUniqueId(), id.toString());
        if (total <= 0) return 0;
        double progress = 1.0 - remainingMillis / (total * 1000.0);
        int level = (int) Math.round(FILL_LEVELS
                * Math.max(0.0, Math.min(1.0, progress)));
        return Math.max(0, Math.min(FILL_LEVELS, level));
    }

    /** Subtitles have no send-without-duration API; sticky captions are re-sent by the HUD refresh. */
    private static final long NOTICE_MILLIS = 3_000L;

    /** Title parts leave the persistent main title (domain cinematics, "FROZEN", …) untouched. */
    private static void subtitle(Player player, Component text, long stayMillis) {
        player.sendTitlePart(TitlePart.SUBTITLE, text);
        player.sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ZERO,
                Duration.ofMillis(Math.max(1L, stayMillis)), Duration.ofMillis(200)));
    }

    public static void showNotice(Player player, String miniMessage, long durationMillis) {
        showNotice(player, ActionBarNotice.Priority.NORMAL, miniMessage, durationMillis, false);
    }

    public static void showNotice(Player player, ActionBarNotice.Priority priority,
                                  String miniMessage, long durationMillis) {
        showNotice(player, priority, miniMessage, durationMillis, false);
    }

    public static void showNotice(Player player, ActionBarNotice.Priority priority,
                                  String miniMessage, long durationMillis, boolean sticky) {
        NOTICES.show(player.getUniqueId(), priority, miniMessage,
                System.currentTimeMillis(), durationMillis, sticky);
        // The action bar belongs to the persistent HUD; notices go to the subtitle line.
        subtitle(player, MM.deserialize(miniMessage), sticky ? NOTICE_MILLIS : durationMillis);
    }

    public static void clearNotice(Player player) {
        NOTICES.clear(player.getUniqueId());
        subtitle(player, Component.empty(), NOTICE_MILLIS);
    }

    /** Re-sends a live sticky caption on the HUD refresh; one-shot notices are not replayed. */
    public static void refreshNotice(Player player) {
        if (player == null) return;
        String message = NOTICES.stickyMessage(player.getUniqueId(), System.currentTimeMillis());
        if (message != null) subtitle(player, MM.deserialize(message), NOTICE_MILLIS);
    }
}
