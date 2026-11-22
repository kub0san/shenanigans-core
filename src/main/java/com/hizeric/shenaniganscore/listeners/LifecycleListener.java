package com.hizeric.shenaniganscore.listeners;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.session.SessionEndReason;
import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Lifecycle cleanup: when the owner of a domain vanishes (dies or disconnects),
 * their dimension collapses immediately instead of leaking entities into the world.
 *
 * <p>A death and a disconnect are not the same ending. A caster that dies inside
 * their own domain gets the shattering outro, while a disconnect is an ordinary
 * end and collapses the barrier normally. The routing decision lives here; the
 * domain session decides what the outro looks like.</p>
 */
public class LifecycleListener implements Listener {

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player caster = event.getEntity();
        endIfActive(caster, SessionEndReason.CASTER_DEATH);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player caster = event.getPlayer();
        // Drop any client-side block overlays bound to the quitter so stale fake blocks and
        // per-player queue state cannot leak across reconnect (F-16, F-48). Quit fires on main.
        ClientBlockUtils.clearPlayer(caster);
        endIfActive(caster, SessionEndReason.NORMAL);
    }

    private void endIfActive(Player caster, SessionEndReason reason) {
        ShenanigansCore core = ShenanigansCore.getInstance();
        // Null-guard the disable window: getInstance()/getSessionManager() go null during teardown (F-40).
        if (core == null || core.getSessionManager() == null) return;
        if (core.getSessionManager().hasActiveSession(caster.getUniqueId())) {
            core.getSessionManager().stopSession(caster.getUniqueId(), reason);
        }
    }
}