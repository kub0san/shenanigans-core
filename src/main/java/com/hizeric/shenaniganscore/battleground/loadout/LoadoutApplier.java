package com.hizeric.shenaniganscore.battleground.loadout;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.ability.AbilityRail;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.List;

public final class LoadoutApplier {

    public record ApplyResult(int slotsInstalled, int railChanges, List<String> warnings) { }

    private LoadoutApplier() { }

    public static ApplyResult apply(ShenanigansCore plugin, Player player) {
        if (player == null) {
            return new ApplyResult(0, 0, List.of("player is null"));
        }
        KitStore kit = GlobalKit.store();
        LoadoutPlan.Result plan = LoadoutPlan.compute(plugin, player.getUniqueId(), kit.kit());

        PlayerInventory inventory = player.getInventory();
        inventory.clear();
        int installed = 0;
        int limit = Math.min(plan.board().length, inventory.getSize());
        for (int i = 0; i < limit; i++) {
            ItemStack item = plan.board()[i];
            if (item == null) continue;
            inventory.setItem(i, item);
            installed++;
        }
        player.updateInventory();

        int changed = 0;
        AbilityRail rail = plugin.getBattlegroundManager().playerStore()
                .get(player.getUniqueId()).rail();
        for (int i = 0; i < AbilityRail.TOTAL; i++) {
            if (!java.util.Objects.equals(rail.get(i), plan.rail()[i])) {
                rail.set(i, plan.rail()[i]);
                changed++;
            }
        }
        if (changed > 0 || plan.railDirty()) {
            plugin.getBattlegroundManager().playerStore().markDirty(player.getUniqueId());
        }
        return new ApplyResult(installed, changed, plan.warnings());
    }
}