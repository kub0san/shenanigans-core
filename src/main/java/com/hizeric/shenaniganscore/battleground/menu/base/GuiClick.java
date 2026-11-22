package com.hizeric.shenaniganscore.battleground.menu.base;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/** A click on a button. The clicking player is already resolved for the handler. */
@FunctionalInterface
public interface GuiClick {
    void on(Player player, InventoryClickEvent event);
}
