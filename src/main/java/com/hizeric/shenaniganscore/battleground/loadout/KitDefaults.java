package com.hizeric.shenaniganscore.battleground.loadout;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/** The immutable built-in kit that seeds a fresh database. Never mutated after construction. */
public final class KitDefaults {

    public static final int BOARD_SIZE = 41;

    private KitDefaults() { }

    /** Hotbar 0-8, inventory 9-35, armour 36-39, off-hand 40. No technique items. */
    public static ItemStack[] items() {
        ItemStack[] items = new ItemStack[BOARD_SIZE];
        items[0] = new ItemStack(Material.DIAMOND_SWORD);
        items[1] = new ItemStack(Material.BOW);
        items[2] = new ItemStack(Material.ARROW, 64);
        items[3] = new ItemStack(Material.ENDER_PEARL, 16);
        items[4] = new ItemStack(Material.GOLDEN_APPLE, 8);
        // Bukkit armour indices: 36 boots, 37 leggings, 38 chestplate, 39 helmet.
        items[36] = new ItemStack(Material.DIAMOND_BOOTS);
        items[37] = new ItemStack(Material.DIAMOND_LEGGINGS);
        items[38] = new ItemStack(Material.DIAMOND_CHESTPLATE);
        items[39] = new ItemStack(Material.DIAMOND_HELMET);
        return items;
    }
}