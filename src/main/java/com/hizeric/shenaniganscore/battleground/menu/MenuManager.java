package com.hizeric.shenaniganscore.battleground.menu;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players currently have a battleground screen open. InventoryFramework
 * owns the click routing, so this only needs an open/closed flag; it exists so the
 * HUD and listeners can stay quiet behind a screen.
 */
public final class MenuManager {

    private static final MenuManager INSTANCE = new MenuManager();

    private final Set<UUID> open = ConcurrentHashMap.newKeySet();
    /** The exact top inventory each player was shown, for hub-reopen identity checks. */
    private final Map<UUID, Inventory> screens = new ConcurrentHashMap<>();

    private MenuManager() { }

    public static MenuManager get() { return INSTANCE; }

    public void markIfOpen(UUID player) {
        if (player != null) open.add(player);
    }

    public void clearIfOpen(UUID player) {
        if (player != null) {
            open.remove(player);
            screens.remove(player);
        }
    }

    public void markIfOpen(Player player, Inventory inventory) {
        if (player == null || inventory == null) return;
        open.add(player.getUniqueId());
        screens.put(player.getUniqueId(), inventory);
    }

    /** True only when the closing inventory was one of our screen chests. */
    public boolean isOurInventory(Player player, Inventory inventory) {
        if (player == null || inventory == null) return false;
        return screens.get(player.getUniqueId()) == inventory;
    }

    public boolean isOpen(UUID player) {
        return player != null && open.contains(player);
    }

    public void markIfOpen(Player player) { markIfOpen(player == null ? null : player.getUniqueId()); }
    public void clearIfOpen(Player player) { clearIfOpen(player == null ? null : player.getUniqueId()); }
    public boolean isOpen(Player player) { return isOpen(player == null ? null : player.getUniqueId()); }

    /** PlugManX/reload hook: forget players whose GUI was open when the plugin disabled. */
    public void clearAll() {
        open.clear();
        screens.clear();
    }
}
