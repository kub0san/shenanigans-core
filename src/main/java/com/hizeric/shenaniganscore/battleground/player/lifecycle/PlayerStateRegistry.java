package com.hizeric.shenaniganscore.battleground.player.lifecycle;

import com.hizeric.shenaniganscore.battleground.player.ArenaInventories;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

/**
 * Single source of truth for every transient per-player fact: staged status, state snapshot,
 * inventory snapshot, anchor, hidden viewers, and dive state. Injectable, clearable, testable.
 */
public final class PlayerStateRegistry {

    public record Entry(PlayerStateSnapshot state,
                        ArenaInventories.Snapshot inventory,
                        Location anchor,
                        Set<UUID> hiddenFrom,
                        boolean staged,
                        boolean diving) {
        Entry withStaged(boolean value) {
            return new Entry(state, inventory, anchor, hiddenFrom, value, diving);
        }
        Entry withDiving(boolean value) {
            return new Entry(state, inventory, anchor, hiddenFrom, staged, value);
        }
        Entry withAnchor(Location value) {
            return new Entry(state, inventory, value, hiddenFrom, staged, diving);
        }
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();
    private final Plugin plugin;

    public PlayerStateRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- staging

    public boolean isStaged(UUID uuid) {
        if (uuid == null) return false;
        try {
            Entry e = entries.get(uuid);
            return e != null && e.staged();
        } catch (Throwable t) {
            if (plugin != null) plugin.getLogger().warning("State check failed for " + uuid + " (failing open): " + t.getMessage());
            return false;
        }
    }

    public boolean isStaged(Player player) {
        return player != null && isStaged(player.getUniqueId());
    }

    public boolean enterStaging(Player player, ArenaInventories.Snapshot inventory) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();
        Entry existing = entries.get(uuid);
        if (existing != null && existing.staged()) return false;

        PlayerStateSnapshot state = existing != null && existing.state() != null
                ? existing.state() : PlayerStateSnapshot.of(player);
        Location anchor = existing != null && existing.anchor() != null
                ? existing.anchor() : player.getLocation().clone();

        Entry entry = new Entry(state, inventory, anchor,
                ConcurrentHashMap.newKeySet(), true, existing != null && existing.diving());
        entries.put(uuid, entry);
        return true;
    }

    public PlayerStateSnapshot releaseStaging(UUID uuid) {
        Entry e = mutate(uuid, x -> x.withStaged(false));
        return e == null ? null : e.state();
    }

    public PlayerStateSnapshot exitStaging(UUID uuid) {
        Entry removed = entries.remove(uuid);
        return removed == null ? null : removed.state();
    }

    public Location anchor(UUID uuid) {
        Entry e = entries.get(uuid);
        return e == null ? null : e.anchor();
    }

    public void setAnchor(UUID uuid, Location anchor) {
        if (uuid == null || anchor == null) return;
        Entry existing = entries.get(uuid);
        if (existing == null) return;
        entries.put(uuid, existing.withAnchor(anchor.clone()));
    }

    public Location anchor(Player player) {
        return player == null ? null : anchor(player.getUniqueId());
    }

    // ---------------------------------------------------------------- visibility

    public void recordHiddenFrom(UUID staged, UUID viewer) {
        if (staged == null || viewer == null) return;
        Entry e = entries.get(staged);
        if (e != null) e.hiddenFrom().add(viewer);
    }

    public Set<UUID> drainHiddenFrom(UUID staged) {
        Entry e = entries.get(staged);
        if (e == null) return Set.of();
        Set<UUID> copy = Set.copyOf(e.hiddenFrom());
        e.hiddenFrom().clear();
        return copy;
    }

    public Set<UUID> stagedUuids() {
        return entries.entrySet().stream()
                .filter(x -> x.getValue().staged())
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    // ---------------------------------------------------------------- diving

    public boolean isDiving(UUID uuid) {
        Entry e = entries.get(uuid);
        return e != null && e.diving();
    }

    public void setDiving(UUID uuid, boolean diving) {
        mutate(uuid, e -> e.withDiving(diving));
    }

    // ---------------------------------------------------------------- admin

    public void remove(UUID uuid) {
        entries.remove(uuid);
    }

    public void clear() {
        entries.clear();
    }

    private Entry mutate(UUID uuid, UnaryOperator<Entry> op) {
        if (uuid == null) return null;
        return entries.computeIfPresent(uuid, (k, e) -> op.apply(e));
    }
}