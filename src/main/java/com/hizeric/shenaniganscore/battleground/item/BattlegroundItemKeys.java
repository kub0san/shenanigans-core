package com.hizeric.shenaniganscore.battleground.item;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Persistent-data identity for battleground custom items.
 *
 * <p>Recognition must never depend on {@link ItemStack#isSimilar}: enchants, lore and the Speed
 * passive all survive a serialize round-trip, but stack comparison is brittle the moment an admin
 * edits the catalogue row. The stamped id is the one durable identity.</p>
 */
public final class BattlegroundItemKeys {

    /** Marks an item as a battleground custom item; the value is the catalog entry id. */
    public static final NamespacedKey ITEM_ID =
            new NamespacedKey("shenaniganscore", "battleground_item");

    private BattlegroundItemKeys() { }

    public static void stamp(ItemMeta meta, String itemId) {
        if (meta == null || itemId == null || itemId.isBlank()) return;
        meta.getPersistentDataContainer().set(ITEM_ID, PersistentDataType.STRING, itemId);
    }

    /** @return the stamped catalog entry id, or null when this is not a battleground item. */
    public static String idOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(ITEM_ID, PersistentDataType.STRING);
    }

    public static boolean matches(ItemStack stack, String itemId) {
        return itemId != null && itemId.equals(idOf(stack));
    }
}
