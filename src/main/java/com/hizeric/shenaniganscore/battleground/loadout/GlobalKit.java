package com.hizeric.shenaniganscore.battleground.loadout;

import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * @deprecated use {@link KitStore} via the plugin lifecycle. This shim keeps the static API
 * alive for third-party callsites that have not migrated yet. New code should inject a
 * {@link KitStore} and never touch this class.
 */
@Deprecated(since = "2.0", forRemoval = true)
public final class GlobalKit {

    public static final int BOARD_SIZE = KitDefaults.BOARD_SIZE;

    private static final KitStore STORE = new KitStore(BOARD_SIZE, KitDefaults.items());

    private GlobalKit() { }

    /** Accessor for callers that want to migrate to the new API incrementally. */
    public static KitStore store() { return STORE; }

    public static ItemStack[] baseItems() { return KitDefaults.items(); }
    public static ItemStack[] kit() { return STORE.kit(); }
    public static ItemStack[] defaults() { return STORE.kit(); }
    public static void setGlobalKit(ItemStack[] items) { STORE.replace(items); }
    public static List<ItemStack> palette() { return STORE.palette(); }
    public static boolean allowed(ItemStack candidate) { return STORE.allowed(candidate); }
}