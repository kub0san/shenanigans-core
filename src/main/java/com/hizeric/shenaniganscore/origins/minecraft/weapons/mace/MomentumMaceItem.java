package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS;

/** Identity and catalogue row for the Momentum Mace. */
public final class MomentumMaceItem {
    public static final String ID = MomentumMaceId.NEW_ID;
    public static final String DISPLAY_NAME = "Momentum Mace";
    public static final int PRICE = 650;
    public static final int DEFAULT_COOLDOWN_SECONDS = 30;
    public static final AbilityId ABILITY_ID = AbilityId.of("minecraft:momentum_mace");

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MomentumMaceItem() { }

    public static int cooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(ITEMS, "momentum-mace", DEFAULT_COOLDOWN_SECONDS);
    }

    public static List<String> lore() {
        return List.of(
                "<gray>A weapon built around momentum.",
                "",
                "<dark_gray>Hit Charges <dark_gray>» <white>" + MomentumMaceChargeManager.maxCharges() + " maximum",
                "<dark_gray>Recharge <dark_gray>» <white>1 charge every " + (MomentumMaceChargeManager.rechargeMs() / 1000L) + "s",
                "",
                "<dark_gray>Slot 1 <dark_gray>» <white>Kinetic Drive <dark_gray>| <gray>" + cooldownSeconds() + "s cooldown",
                "<dark_gray>Sneak + Slot 1 <dark_gray>» <white>Meteor Dive",
                "",
                "<dark_gray>Passive <dark_gray>» <white>Impact Rebound");
    }

    public static ItemStack build() {
        ItemStack stack = new ItemStack(Material.MACE);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;
        meta.displayName(MM.deserialize("<aqua><bold>Momentum Mace"));
        meta.lore(lore().stream().map(MomentumMaceItem::mini).toList());
        meta.addEnchant(Enchantment.DENSITY, 5, true);
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        BattlegroundItemKeys.stamp(meta, ID);
        stack.setItemMeta(meta);
        return stack;
    }

    public static ShopEntry catalogEntry() {
        return ShopEntry.customItem(ID, Material.MACE, DISPLAY_NAME, PRICE, ABILITY_ID.toString(), build().serialize());
    }

    public static boolean isMomentumMace(ItemStack stack) {
        return BattlegroundItemKeys.matches(stack, ID);
    }

    private static Component mini(String miniMessage) { return MM.deserialize(miniMessage); }
}
