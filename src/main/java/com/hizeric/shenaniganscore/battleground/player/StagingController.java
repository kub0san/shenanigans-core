package com.hizeric.shenaniganscore.battleground.player;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.RespawnTarget;
import com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerStateRegistry;
import com.hizeric.shenaniganscore.battleground.player.lifecycle.PlayerStateSnapshot;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class StagingController {

    private final ShenanigansCore plugin;
    private final PlayerStateRegistry registry;
    private final HiddenViewers hiddenViewers = new HiddenViewers();

    public StagingController(ShenanigansCore plugin) {
        this(plugin, new PlayerStateRegistry(plugin));
    }

    public StagingController(ShenanigansCore plugin, PlayerStateRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public PlayerStateRegistry registry() { return registry; }

    public void enter(Player player) {
        if (player == null) return;
        ArenaInventories.Snapshot inventory = new ArenaInventories.Snapshot(
                player.getInventory().getContents(), player.getItemOnCursor());
        if (!registry.enterStaging(player, inventory)) return; // already staged

        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setCollidable(false);
        player.setFoodLevel(20);
        player.setFireTicks(0);
        player.setFallDistance(0f);

        BattlegroundLock.enter(player.getUniqueId());

        hideFromOnline(player);
    }

    public void release(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        PlayerStateSnapshot snapshot = registry.releaseStaging(uuid);
        if (snapshot == null) {
            BattlegroundLock.exit(uuid);
            return;
        }
        try {
            snapshot.restoreTo(player);
        } finally {
            showToHiddenViewers(uuid);
            BattlegroundLock.exit(uuid);
        }
    }

    public void exit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard.get().clearTransit(uuid);
        PlayerStateSnapshot snapshot = registry.exitStaging(uuid);
        if (snapshot == null) {
            BattlegroundLock.exit(uuid);
            showToHiddenViewers(uuid);
            return;
        }
        try {
            com.hizeric.shenaniganscore.battleground.BattlegroundManager manager =
                    plugin == null ? null : plugin.getBattlegroundManager();
            ArenaRegion region = (manager == null) ? null : manager.arenaStore().region();
            Location destination = RespawnTarget.resolve(
                    region == null ? null : region.spawn(),
                    player.getWorld().getSpawnLocation(), region);
            player.teleport(destination);
            snapshot.restoreTo(player);
        } finally {
            showToHiddenViewers(uuid);
            BattlegroundLock.exit(uuid);
        }
    }

    public void repairLeaked(Player player) {
        repair(player);
    }

    public void repair(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        com.hizeric.shenaniganscore.battleground.arena.ArenaEntryGuard.get().clearTransit(uuid);
        PlayerStateSnapshot snapshot = registry.exitStaging(uuid);
        if (snapshot == null) {
            PlayerStateSnapshot.safeFallback(player).restoreTo(player);
        } else {
            snapshot.restoreTo(player);
        }
        showToHiddenViewers(uuid);
        BattlegroundLock.exit(uuid);
    }

    public boolean isStaged(Player player) {
        return player != null && registry.isStaged(player.getUniqueId());
    }

    public boolean hasSnapshot(UUID uuid) {
        return uuid != null && registry.anchor(uuid) != null;
    }

    public Set<UUID> stagedUuids() {
        return registry.stagedUuids();
    }

    public void rememberViewer(UUID staged, UUID viewer) {
        registry.recordHiddenFrom(staged, viewer);
    }

    public void removeSnapshot(UUID uuid) {
        if (uuid != null) registry.remove(uuid);
    }

    public Location anchor(Player player, ArenaRegion region) {
        if (player == null) return null;
        return registry.anchor(player.getUniqueId());
    }

    public void clear() {
        for (UUID uuid : registry.stagedUuids()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) exit(player);
            else {
                registry.exitStaging(uuid);
                BattlegroundLock.exit(uuid);
            }
        }
        registry.clear();
        BattlegroundLock.clear();
    }

    public void clampMovement(Player player, ArenaRegion region) {
        if (!isStaged(player)) return;
        Location anchor = anchor(player, region);
        if (anchor == null) return;
        Location current = player.getLocation();
        if (MoveClamp.outside(current, anchor, 2.25)) {
            player.teleport(MoveClamp.snap(current, anchor));
        }
    }

    // ------------------------------------------------------------------ new API

    public void hideStagedFrom(Player joiner) {
        if (joiner == null) return;
        for (UUID staged : registry.stagedUuids()) {
            Player target = Bukkit.getPlayer(staged);
            if (target != null && !target.equals(joiner)) {
                joiner.hidePlayer(plugin, target);
                registry.recordHiddenFrom(staged, joiner.getUniqueId());
            }
        }
    }

    private void hideFromOnline(Player staged) {
        UUID uuid = staged.getUniqueId();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(staged)) continue;
            other.hidePlayer(plugin, staged);
            registry.recordHiddenFrom(uuid, other.getUniqueId());
        }
    }

    private void showToHiddenViewers(UUID staged) {
        for (UUID viewerId : registry.drainHiddenFrom(staged)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            Player target = Bukkit.getPlayer(staged);
            if (viewer != null && target != null) viewer.showPlayer(plugin, target);
        }
    }
}