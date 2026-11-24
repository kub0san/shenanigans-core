package com.hizeric.shenaniganscore.battleground.economy;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import com.hizeric.shenaniganscore.battleground.combat.CombatTracker;
import com.hizeric.shenaniganscore.battleground.combat.KillFeed;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.player.PlayerStore;
import com.hizeric.shenaniganscore.battleground.shop.ShopCatalog;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.bukkit.entity.Player;

/** Flat kill rewards and streak-gated casting. Ownership is catalog-only (no free unlocks). */
public final class EconomyService {

    private final PlayerStore store;
    private final CombatTracker combat;
    private final ShopCatalog catalog;

    private int creditsResetThisRound;

    public EconomyService(PlayerStore store, CombatTracker combat, ShopCatalog catalog) {
        this.store = store;
        this.combat = combat;
        this.catalog = catalog;
    }

    /** @return credits actually granted to the killer. */
    public int awardKill(Player killer, Player victim) {
        if (killer == null || victim == null || killer.equals(victim)) return 0;

        BattlegroundPlayer killerData = store.get(killer.getUniqueId());
        BattlegroundPlayer victimData = store.get(victim.getUniqueId());
        if (killerData == null || victimData == null) return 0;
        victimData.recordDeath();
        store.markDirty(victim.getUniqueId());

        // Flat credits per kill: no assists, bounties, streak bonuses or anti-farm logic.
        int reward = BattlegroundDefaults.KILL_REWARD;

        killerData.recordKill();
        killerData.addCredits(reward);
        store.markDirty(killer.getUniqueId());

        KillFeed.kill(killer.getName(), victim.getName(), reward, killerData.streak());
        return reward;
    }

    /** Called by the round reset. */
    public void rollNewRound() {
        store.resetRounds();
        combat.clearAll();
        creditsResetThisRound = 0;
        if (BattlegroundDefaults.RESET_CREDITS) {
            store.resetCredits();
            creditsResetThisRound = store.all().size();
        }
    }

    /** Unlocks an ability with credits. @return true on success. */
    public boolean purchase(Player player, AbilityDefinition definition) {
        return purchase(player, definition, definition == null ? 0 : definition.price());
    }

    /**
     * Unlocks an ability at a catalogue-controlled price. AbilityDefinition.price is
     * only a default; the shop.yml catalogue is the live source of truth.
     */
    public boolean purchase(Player player, AbilityDefinition definition, int priceOverride) {
        if (player == null || definition == null) return false;
        ShopEntry entry = catalog == null ? null : catalog.entryForAbility(definition.id().toString());
        if (entry == null) return false;
        return purchaseCatalog(player, entry.id(), priceOverride)
                == BattlegroundPlayer.PurchaseResult.SUCCESS;
    }

    /** Buys a catalog entry. The purchase is permanent; the streak gate is checked at cast time. */
    public BattlegroundPlayer.PurchaseResult purchaseCatalog(Player player, String entryId, int price) {
        if (player == null) return BattlegroundPlayer.PurchaseResult.INVALID_ENTRY;
        ShopEntry entry = catalog == null ? null : catalog.entry(entryId);
        if (entry == null || !entry.enabled()) return BattlegroundPlayer.PurchaseResult.INVALID_ENTRY;
        BattlegroundPlayer data = store.get(player.getUniqueId());
        BattlegroundPlayer.PurchaseResult result = data.purchase(entry.id(), Math.max(0, entry.price()));
        if (result == BattlegroundPlayer.PurchaseResult.SUCCESS) store.markDirty(player.getUniqueId());
        return result;
    }

    /** All inputs of the cast gate, computed once for every cast source. */
    public record CastGate(boolean owned, int required, int streak, boolean streakMet) {

        /** The same gate as catalog display data, carrying the entry's price for the HUD. */
        public ShopEntry.Gate display(ShopEntry entry) {
            return new ShopEntry.Gate(owned, streak, required, entry == null ? 0 : entry.price());
        }
    }

    /**
     * Applies the shop policy to a gate. With killstreak requirements off, the gate is
     * neutral: no streak is required and the requirement is always reported as met.
     */
    public static CastGate applyPolicy(CastGate gate, boolean killstreakRequirements) {
        if (!killstreakRequirements) {
            return new CastGate(gate.owned(), 0, gate.streak(), true);
        }
        return gate;
    }

    public CastGate castGate(Player player, AbilityId id, ShopCatalog catalog) {
        if (player == null || id == null) return new CastGate(false, 0, 0, false);
        BattlegroundPlayer data = store.get(player.getUniqueId());
        ShopEntry entry = catalog == null ? null : catalog.entryForAbility(id.toString());
        if (entry == null || !entry.enabled()) {
            return new CastGate(false, 0, data == null ? 0 : data.streak(), false);
        }
        if (data == null) return new CastGate(false, 0, 0, false);
        int required = Math.max(0, entry.streakRequired());
        boolean owned = data.owns(entry.id());
        int streak = data.streak();
        return applyPolicy(new CastGate(owned, required, streak, streak >= required),
                ShopPolicyConfig.current().killstreakRequirements());
    }

    /** The single cast gate used by commands, the global caster gate, and loadout checks. */
    public boolean canCast(Player player, AbilityId id, ShopCatalog catalog) {
        CastGate gate = castGate(player, id, catalog);
        return gate.owned() && gate.streakMet();
    }

    /** Short end-of-round leaderboard line. */
    public String roundSummary() {
        BattlegroundPlayer topKills = null;
        BattlegroundPlayer topCredits = null;
        for (BattlegroundPlayer player : store.all()) {
            if (topKills == null || player.kills() > topKills.kills()) topKills = player;
            if (topCredits == null || player.credits() > topCredits.credits()) topCredits = player;
        }
        if (topKills == null) return "<gray>No rounds recorded yet.";
        return "<gold>Top kills <white>" + topKills.kills()
                + " <gold>| Richest <white>" + topCredits.credits() + "c";
    }

    public int creditsResetThisRound() { return creditsResetThisRound; }
}
