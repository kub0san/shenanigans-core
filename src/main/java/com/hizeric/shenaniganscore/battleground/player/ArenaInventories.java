package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ArenaInventories {

    public record Snapshot(ItemStack[] contents, ItemStack cursor) {
        public Snapshot {
            contents = contents == null ? new ItemStack[0] : contents.clone();
        }

        /** Restores inventory and cursor in one call. Main thread only. */
        public void restoreTo(Player player) {
            player.getInventory().setContents(contents);
            player.setItemOnCursor(cursor);
            player.updateInventory();
        }
    }

    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();

    public void captureOnce(UUID uuid, ItemStack[] contents, ItemStack cursor) {
        if (uuid == null) return;
        snapshots.putIfAbsent(uuid, new Snapshot(contents, cursor));
    }

    /**
     * Refreshes an existing snapshot (or starts a new one). Unlike {@link #captureOnce}, a later
     * capture replaces the stale first-deploy contents, so hub items acquired in between survive
     * the next round boundary instead of being overwritten by the original kit.
     */
    public void replace(UUID uuid, Snapshot snapshot) {
        if (uuid == null || snapshot == null) return;
        snapshots.put(uuid, snapshot);
    }

    public boolean has(UUID uuid) {
        return uuid != null && snapshots.containsKey(uuid);
    }

    public Snapshot take(UUID uuid) {
        return uuid == null ? null : snapshots.remove(uuid);
    }

    public void forget(UUID uuid) {
        if (uuid != null) snapshots.remove(uuid);
    }

    public int snapshotCount() { return snapshots.size(); }
    public void clear() { snapshots.clear(); }
}