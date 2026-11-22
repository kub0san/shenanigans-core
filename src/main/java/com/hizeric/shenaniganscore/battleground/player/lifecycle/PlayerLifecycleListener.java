package com.hizeric.shenaniganscore.battleground.player.lifecycle;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.player.MoveClamp;
import com.hizeric.shenaniganscore.battleground.player.PlayerStore;
import com.hizeric.shenaniganscore.battleground.player.StagingController;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;

public final class PlayerLifecycleListener implements Listener {

    private final ShenanigansCore plugin;
    private final PlayerStateRegistry registry;
    private final PlayerStore store;
    private final StagingController staging;

    public PlayerLifecycleListener(ShenanigansCore plugin, PlayerStateRegistry registry,
                                   PlayerStore store, StagingController staging) {
        this.plugin = plugin;
        this.registry = registry;
        this.store = store;
        this.staging = staging;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (store != null) store.preload(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (staging != null) staging.hideStagedFrom(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        if (store != null) store.markDirty(uuid);
        if (registry != null) registry.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (registry == null || !registry.isStaged(player.getUniqueId())) return;
        Location anchor = registry.anchor(player.getUniqueId());
        if (anchor == null) return;

        Location to = event.getTo();
        if (!MoveClamp.outside(to, anchor, 2.25)) return;

        event.setTo(MoveClamp.clamp(to, anchor));
        player.setVelocity(new Vector(0, 0, 0));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (registry != null && registry.isStaged(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (registry != null && registry.isStaged(event.getPlayer().getUniqueId())
                && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
        }
    }
}