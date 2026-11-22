package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.ability.HeldItemAbility;
import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.ability.CastResult;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.UUID;

/**
 * The one cast gate plus the held-item lookup it depends on. Split out of {@link BattlegroundManager}
 * (C1) so ability-policy changes cannot touch round lifecycle or deploy code.
 */
final class AbilityGateService {
    private final BattlegroundManager m;

    AbilityGateService(BattlegroundManager manager) {
        this.m = manager;
    }

    /** Registers the only ability gate on the plugin's caster. */
    void register() {
        m.plugin.getAbilityCaster().addGate((player, definition, source) -> {
            UUID uuid = player.getUniqueId();
            if (!m.isDeployed(uuid) && !m.staging.isStaged(player)) return CastResult.OK;
            if (!m.isDeployed(uuid)) return CastResult.NOT_IN_ARENA;
            if (!m.economy.canCast(player, definition.id(), m.shopCatalog)) {
                return CastResult.NOT_OWNED;
            }
            // A custom item's ability is cast from the item: ownership alone is not enough, the
            // matching stack must be in hand.
            ShopEntry entry = m.shopCatalog.entryForAbility(definition.id().toString());
            if (entry != null && entry.isCustomItem()
                    && !HeldItemAbility.matches(definition.id(), heldItemAbility(player))) {
                return CastResult.HELD_ITEM_REQUIRED;
            }
            return CastResult.OK;
        });
    }

    AbilityId heldItemAbility(Player player) {
        return heldItemAbility(player, heldCustomItem(player));
    }

    /** Resolves the custom stack from either hand; the Speed modifier itself applies to both. */
    ItemStack heldCustomItem(Player player) {
        if (player == null) return null;
        PlayerInventory inventory = player.getInventory();
        ItemStack mainHand = inventory.getItemInMainHand();
        if (m.shopCatalog != null && m.shopCatalog.customItemFor(mainHand) != null) return mainHand;
        ItemStack offHand = inventory.getItemInOffHand();
        if (m.shopCatalog != null && m.shopCatalog.customItemFor(offHand) != null) return offHand;
        return mainHand;
    }

    AbilityId heldItemAbility(Player player, ItemStack held) {
        if (player == null || m.shopCatalog == null) return null;
        ShopEntry entry = m.shopCatalog.customItemFor(held);
        if (entry == null) return null;
        BattlegroundPlayer data = m.playerStore().get(player.getUniqueId());
        if (data == null || !data.owns(entry.id())) return null;
        return HeldItemAbility.of(entry);
    }
}
