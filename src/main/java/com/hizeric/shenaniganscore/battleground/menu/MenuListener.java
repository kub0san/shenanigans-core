package com.hizeric.shenaniganscore.battleground.menu;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.menu.shell.HubScreen;
import com.hizeric.shenaniganscore.battleground.menu.shell.MenuLatch;import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Guards battleground screens: cancel drags (every slot is rendered from state) and
 * keep a staged player latched into the hub. Clicks are left to InventoryFramework.
 */
public final class MenuListener implements Listener {

    private final ShenanigansCore plugin;

    public MenuListener(ShenanigansCore plugin) {
        this.plugin = plugin;
    }

    /**
     * No-op safety net: any click in a virtual battleground screen must never move a stack.
     * InventoryFramework cancels its own pane clicks; the player-inventory half of a
     * shifting click is covered here.
     */
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!MenuManager.get().isOpen(player)) return;
        if (event.getClickedInventory() == null) return;
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }
        // Keyboard clicks are the only way to arrange kit items in the real inventory;
        // cancel them only in the virtual (top) half of a screen.
        if (event.getClick().isKeyboardClick()
                && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!MenuManager.get().isOpen(player)) return;        event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        boolean ours = MenuManager.get().isOurInventory(player, event.getInventory());
        MenuManager.get().clearIfOpen(player);
        if (!ours) return;
        boolean staged = plugin.getBattlegroundManager().staging().isStaged(player);
        if (!MenuLatch.get().shouldReopen(player, staged)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (MenuLatch.get().shouldReopen(player, staged)
                    && plugin.getBattlegroundManager().staging().isStaged(player)) {
                new HubScreen(plugin, player).show();
            }
        });
    }
}
