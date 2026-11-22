package com.hizeric.shenaniganscore.core.session;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Origin-agnostic movement-lock query.
 *
 * <p>Every enforcement layer in {@code listeners.MovementListener} (move, velocity,
 * flight, vehicle entry), {@code listeners.ActionLockListener} and the arena's fire
 * diagnostic asks this class one question: "is this player currently hard-locked by
 * an active domain?" Nothing keeps its own list — the answer is derived from the live
 * sessions through {@link DomainSession#isMovementLocked(Player)}, so it cannot leak
 * and needs no cleanup.</p>
 *
 * <p>Creative and spectator players and the caster are never locked. <b>Fail-open:</b>
 * if anything goes wrong, this returns {@code false}. A broken plugin must never trap
 * a player by accident.</p>
 */
public final class Immobilize {

    private Immobilize() { }

    public static boolean isMovementLocked(Player player) {
        if (player == null) return false;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null) return false; // shutting down — fail open silently
        try {
            GameMode mode = player.getGameMode();
            if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;

            for (DomainSession session : plugin.getSessionManager().getActiveSessions().values()) {
                if (session.isMovementLocked(player)) return true;
            }
            return false;
        } catch (Throwable t) {
            plugin.getLogger().warning("Immobilize check failed for " + player.getName() + " (failing open): " + t.getMessage());
            return false;
        }
    }
}
