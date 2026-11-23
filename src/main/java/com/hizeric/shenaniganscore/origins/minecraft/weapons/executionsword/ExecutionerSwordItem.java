package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

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

/**
 * The Executioner's Sword custom item: a netherite blade whose ability is triggered by
 * {@code /ability 1} while holding it. Casting arms the next landed sword hit, which is
 * resolved as either a heavy uppercut (target above {@link #EXECUTE_THRESHOLD} HP) or a
 * guaranteed-kill execution (target at or below the threshold).
 */
public final class ExecutionerSwordItem {

    /** Catalog entry id, also the persistent-data value stamped on the item. */
    public static final String ID = ExecutionerSwordId.NEW_ID;

    public static final String DISPLAY_NAME = "Executioner's Sword";

    /** Catalogue price in credits. */
    public static final int PRICE = 500;
    /** Shipped executioner cooldown; the live value comes from {@code abilities.items.executioner-sword.cooldown}. */
    public static final int DEFAULT_COOLDOWN_SECONDS = 90;

    public static final AbilityId ABILITY_ID = AbilityId.of("minecraft:executioner_sword");

    /** HP threshold (3 hearts) at contact that decides uppercut vs execution. */
    public static final double EXECUTE_THRESHOLD = 6.0;

    /** Arming window in milliseconds after casting. */
    public static final long ARM_DURATION_MS = 6000L;

    public static final int SHARPNESS_LEVEL = 5;
    public static final int UNBREAKING_LEVEL = 3;
    public static final int FIRE_ASPECT_LEVEL = 2;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ExecutionerSwordItem() {
    }

    /** Live executioner cooldown in seconds, swapped by {@code /bg reload}. */
    public static int cooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(ITEMS, "executioner-sword", DEFAULT_COOLDOWN_SECONDS);
    }

    /** Raw MiniMessage lore lines, exposed so tuning can be asserted without a Bukkit server. */
    public static List<String> lore() {
        return List.of(
                "<gray>Heavy uppercut above 3 hearts.",
                "",
                "<dark_gray>Slot 1 <dark_gray>» <white>Executioner's Sword <dark_gray>| <gray>" + cooldownSeconds() + "s cooldown",
                "<dark_gray>Below 3 hearts <dark_gray>» <white>Final Verdict");
    }

    public static ItemStack build() {
        ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(MM.deserialize("<dark_red><bold>Executioner's Sword"));
        meta.lore(lore().stream().map(ExecutionerSwordItem::mini).toList());

        meta.addEnchant(Enchantment.SHARPNESS, SHARPNESS_LEVEL, true);
        meta.addEnchant(Enchantment.UNBREAKING, UNBREAKING_LEVEL, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, FIRE_ASPECT_LEVEL, true);

        BattlegroundItemKeys.stamp(meta, ID);

        stack.setItemMeta(meta);
        return stack;
    }

    /** The catalogue row the shop seeds on load. */
    public static ShopEntry catalogEntry() {
        return ShopEntry.customItem(
                ID,
                Material.NETHERITE_SWORD,
                DISPLAY_NAME,
                PRICE,
                ABILITY_ID.toString(),
                build().serialize()
        );
    }

    public static boolean isExecutionerSword(ItemStack stack) {
        return BattlegroundItemKeys.matches(stack, ID);
    }

    private static Component mini(String miniMessage) {
        return MM.deserialize(miniMessage);
    }
}
