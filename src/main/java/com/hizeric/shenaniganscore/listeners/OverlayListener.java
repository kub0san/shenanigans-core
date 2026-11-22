package com.hizeric.shenaniganscore.listeners;

import com.hizeric.shenaniganscore.core.network.DomainBlockOverlayManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Keeps client-side overlay state in sync across joins, respawns, world changes and teleports.
 *
 * <p>Domains that layer targeted block overlays rely on this listener to be
 * resend-safe: switching worlds, respawning after death or teleporting far
 * enough to drop/re-enter an overlay's range must trigger a re-sync so the
 * client never sees a stale mix of real and fake blocks.</p>
 */
public final class OverlayListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        DomainBlockOverlayManager.get().resendTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        DomainBlockOverlayManager.get().resendTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        DomainBlockOverlayManager.get().resendTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        DomainBlockOverlayManager.get().resendTo(event.getPlayer());
    }
}
