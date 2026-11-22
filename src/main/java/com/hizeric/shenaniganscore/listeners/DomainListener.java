package com.hizeric.shenaniganscore.listeners;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Keeps the fake-block illusion intact inside an open domain.
 *
 * <p>The client sees blackstone/bone where the server holds grass and dirt.
 * If a player “broke” a fake block, the dig packet would land on the real
 * block underneath — letting them chip the domain apart one ghost click at a
 * time. So breaks are locked for everyone inside an active domain, until the
 * domain hands the real terrain back.</p>
 */
public class DomainListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // If the player has an active domain, or is inside one, cancel the break.
        // Null-guard the disable window: getInstance()/getSessionManager() go null during teardown (F-40).
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || plugin.getSessionManager() == null) return;
        plugin.getSessionManager().getActiveSessions().forEach((uuid, session) -> {
            if (event.isCancelled()) return;
            if (session.getCenter().getWorld() == event.getBlock().getWorld()) {
                if (session.getCenter().distanceSquared(event.getBlock().getLocation()) <= (session.getRadius() * session.getRadius())) {
                    event.setCancelled(true);
                }
            }
        });
    }
}
