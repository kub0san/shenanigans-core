package com.hizeric.shenaniganscore.battleground.menu.base;

import com.github.stefvanschie.inventoryframework.gui.type.ChestGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;

import java.util.function.IntPredicate;

/**
 * Click protection for every screen. Three shapes:
 * <ul>
 *   <li>{@link #readOnly} — buttons and display items; nothing moves in or out.</li>
 *   <li>{@link #editable} — editors; only editable slots accept clicks.</li>
 *   <li>{@link #takeOnly} — items may be taken out but never put in.</li>
 * </ul>
 *
 * <p>In IF 0.12.x the dispatch order is global click → top/bottom click → GuiItem
 * handlers. Cancelling an event therefore never stops a button from firing; it
 * only stops the vanilla inventory action.</p>
 */
public final class GuiGuard {
    private GuiGuard() {
    }

    private static boolean allowed(Player clicker, String permission) {
        return permission == null || clicker.hasPermission(permission);
    }

    public static void readOnly(ChestGui chest, String permission, int rows) {
        int size = rows * GuiLayout.COLUMNS;

        chest.setOnGlobalClick(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                event.setCancelled(true);
                return;
            }
            if (!allowed(clicker, permission)) {
                event.setCancelled(true);
                clicker.closeInventory();
                return;
            }
            // Shift-click from the player's inventory and double-click "collect to cursor"
            // would otherwise move items in or out of this screen.
            InventoryAction action = event.getAction();
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                    || action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
        });
        chest.setOnTopClick(event -> event.setCancelled(true));
        chest.setOnGlobalDrag(event -> {
            for (int raw : event.getRawSlots()) {
                if (raw < size) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    /**
     * Protection for screens whose items the player may only take out of.
     *
     * @param takeableSlot tells whether a raw slot may be emptied by the player
     */
    public static void takeOnly(ChestGui chest, String permission, int rows, IntPredicate takeableSlot) {
        int size = rows * GuiLayout.COLUMNS;

        chest.setOnGlobalClick(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                event.setCancelled(true);
                return;
            }
            if (!allowed(clicker, permission)) {
                event.setCancelled(true);
                clicker.closeInventory();
                return;
            }
            InventoryAction action = event.getAction();
            int raw = event.getRawSlot();
            if (action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && raw >= size) {
                event.setCancelled(true);
                return;
            }
            if (raw < size) {
                switch (action) {
                    case PLACE_ALL, PLACE_ONE, PLACE_SOME, SWAP_WITH_CURSOR,
                         HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> event.setCancelled(true);
                    default -> { }
                }
            }
        });
        chest.setOnTopClick(event -> {
            if (!takeableSlot.test(event.getRawSlot())) {
                event.setCancelled(true);
            }
        });
        chest.setOnGlobalDrag(event -> {
            for (int raw : event.getRawSlots()) {
                if (raw >= 0 && raw < size) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }

    /**
     * Protection for screens that move items, such as the editors.
     *
     * @param editableSlot tells whether a raw slot may hold a player-placed item
     */
    public static void editable(ChestGui chest, String permission, int rows, IntPredicate editableSlot) {
        int size = rows * GuiLayout.COLUMNS;

        chest.setOnGlobalClick(event -> {
            if (!(event.getWhoClicked() instanceof Player clicker)) {
                event.setCancelled(true);
                return;
            }
            if (!allowed(clicker, permission)) {
                event.setCancelled(true);
                clicker.closeInventory();
                return;
            }
            InventoryAction action = event.getAction();
            if (action == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
                return;
            }
            if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY && event.getRawSlot() >= size) {
                event.setCancelled(true);
            }
        });
        chest.setOnTopClick(event -> {
            if (!editableSlot.test(event.getRawSlot())) {
                event.setCancelled(true);
            }
        });
        chest.setOnGlobalDrag(event -> {
            for (int raw : event.getRawSlots()) {
                if (raw >= 0 && raw < size && !editableSlot.test(raw)) {
                    event.setCancelled(true);
                    return;
                }
            }
        });
    }
}
