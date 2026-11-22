package com.hizeric.shenaniganscore.battleground.loadout;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.ability.AbilityBindPlanner;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import com.hizeric.shenaniganscore.battleground.ability.HeldItemAbility;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class LoadoutPlan {

    public record Result(ItemStack[] board, AbilityId[] rail, boolean railDirty, List<String> warnings) {
        public Result {
            board = board == null ? new ItemStack[0] : board.clone();
            rail = rail == null ? new AbilityId[AbilityRail.TOTAL] : rail.clone();
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
        }
        @Override public ItemStack[] board() { return board.clone(); }
        @Override public AbilityId[] rail() { return rail.clone(); }
    }

    private LoadoutPlan() { }

    public static Result compute(ShenanigansCore plugin, UUID uuid, ItemStack[] fallback) {
        List<String> warnings = new ArrayList<>();
        BattlegroundPlayer data = plugin.getBattlegroundManager().playerStore().get(uuid);
        if (data == null) {
            warnings.add("no player record; using fallback layout");
            return new Result(fallback, new AbilityId[AbilityRail.TOTAL], false, warnings);
        }

        LoadoutCodec.DecodeResult decoded = LoadoutCodec.decode(data.layoutData(), fallback);
        if (decoded.error() != null) {
            warnings.add("layout: " + decoded.error());
        }

        AbilityRail rail = data.rail();
        boolean dirty = false;

        // Slot 0 is derived from the held item on every HUD tick.
        if (rail.get(0) != null) {
            rail.set(0, null);
            dirty = true;
        }

        Set<AbilityId> valid = new HashSet<>();
        AbilityId firstPick = null;
        boolean needFallback = rail.get(AbilityRail.NORMAL_SLOT) == null
                && rail.get(AbilityRail.ULTIMATE_SLOT) == null;
        for (AbilityDefinition def : plugin.getAbilityRegistry().all()) {
            if (def.hidden()) continue;
            if (isItemBound(plugin, def.id())) continue;
            if (!ownsEntry(plugin, data, def.id())) continue;
            valid.add(def.id());
            if (needFallback && firstPick == null) firstPick = def.id();
        }

        AbilityBindPlanner.Plan bindPlan = AbilityBindPlanner.plan(
                rail.get(AbilityRail.NORMAL_SLOT),
                rail.get(AbilityRail.ULTIMATE_SLOT),
                valid,
                id -> isUltimate(plugin, id),
                firstPick);
        AbilityId[] plan = bindPlan.slots();

        for (int i = AbilityRail.NORMAL_SLOT; i < AbilityRail.TOTAL; i++) {
            AbilityId next = plan[i];
            if (!Objects.equals(rail.get(i), next)) {
                rail.set(i, next);
                dirty = true;
            }
        }
        return new Result(decoded.items(), railSnapshot(rail), dirty, warnings);
    }

    private static boolean ownsEntry(ShenanigansCore plugin, BattlegroundPlayer data, AbilityId id) {
        if (id == null) return false;
        ShopEntry entry = plugin.getBattlegroundManager().shopCatalog()
                .entryForAbility(id.toString());
        return entry != null && entry.isAbility() && data.owns(entry.id());
    }

    private static boolean isItemBound(ShenanigansCore plugin, AbilityId id) {
        if (id == null) return false;
        ShopEntry entry = plugin.getBattlegroundManager().shopCatalog()
                .entryForAbility(id.toString());
        return entry != null && entry.isCustomItem();
    }

    private static boolean isUltimate(ShenanigansCore plugin, AbilityId id) {
        if (id == null) return false;
        ShopEntry entry = plugin.getBattlegroundManager().shopCatalog()
                .entryForAbility(id.toString());
        return entry != null && entry.isUltimate();
    }

    private static AbilityId[] railSnapshot(AbilityRail rail) {
        AbilityId[] snapshot = new AbilityId[AbilityRail.TOTAL];
        for (int i = 0; i < AbilityRail.TOTAL; i++) snapshot[i] = rail.get(i);
        return snapshot;
    }
}