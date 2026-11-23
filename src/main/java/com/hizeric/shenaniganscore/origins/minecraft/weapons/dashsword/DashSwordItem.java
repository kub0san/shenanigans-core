package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

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
 * The v1 catalogue's only custom item: a netherite blade that carries its own dash ability and a
 * passive that refunds one second of dash cooldown per landed hit.
 *
 * <p>The refund lives in {@link DashSwordHitListener}, not in the item: a held movement-speed
 * modifier stacked additively with Speed potions, so the item now only owns its durable identity.
 * The ability id is stamped in persistent data so the catalog can recognise the item even after an
 * admin edits the delivered stack.</p>
 */
public final class DashSwordItem {

    /** Catalog entry id, also the persistent-data value stamped on the item. */
    public static final String ID = DashSwordId.NEW_ID;

    public static final String DISPLAY_NAME = "Dash Sword";

    /** Catalogue price in credits. */
    public static final int PRICE = 350;
    /** Shipped dash cooldown; the live value comes from {@code abilities.items.dash-sword.cooldown}. */
    public static final int DEFAULT_COOLDOWN_SECONDS = 30;

    public static final AbilityId ABILITY_ID = AbilityId.of("minecraft:dash_sword");

    public static final int SHARPNESS_LEVEL = 5;
    public static final int UNBREAKING_LEVEL = 3;
    public static final int FIRE_ASPECT_LEVEL = 2;
    public static final String LORE_LINE = "Self explanatory...";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private DashSwordItem() { }

    /** Live dash cooldown in seconds, swapped by {@code /bg reload}. */
    public static int cooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", DEFAULT_COOLDOWN_SECONDS);
    }

    /** Raw MiniMessage lore lines, exposed so tuning can be asserted without a Bukkit server. */
    public static List<String> lore() {
        return List.of(
                "<gray>" + LORE_LINE,
                "",
                "<dark_gray>Slot 1 <dark_gray>» <white>Dash <dark_gray>| <gray>" + cooldownSeconds() + "s cooldown",
                "<dark_gray>Passive <dark_gray>» <white>-1s dash cooldown per hit");
    }

    public static ItemStack build() {
        ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.displayName(MM.deserialize("<aqua><bold>Dash Sword"));
        meta.lore(lore().stream().map(DashSwordItem::mini).toList());

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

    public static boolean isDashSword(ItemStack stack) {
        return BattlegroundItemKeys.matches(stack, ID);
    }

    private static Component mini(String miniMessage) {
        return MM.deserialize(miniMessage);
    }
}
