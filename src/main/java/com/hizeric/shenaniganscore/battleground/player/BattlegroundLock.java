package com.hizeric.shenaniganscore.battleground.player;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Single source of truth for "this player is in the battleground staging/menu state".
 *
 * <p>Every enforcement layer (damage, movement, drops, commands, inventory) asks this one class,
 * the same way {@code Immobilize.isMovementLocked} works for domains. Nothing keeps its own list.
 * The check is fail-open: a broken plugin must never trap a player.</p>
 */
public final class BattlegroundLock {

    private static final Set<UUID> STAGED = ConcurrentHashMap.newKeySet();

    private BattlegroundLock() { }

    public static void enter(UUID uuid) {
        if (uuid != null) STAGED.add(uuid);
    }

    public static void exit(UUID uuid) {
        if (uuid != null) STAGED.remove(uuid);
    }

    public static boolean isStaged(UUID uuid) {
        return uuid != null && STAGED.contains(uuid);
    }

    public static boolean isStaged(Player player) {
        return player != null && isStaged(player.getUniqueId());
    }

    /** Fail-open alias used by event listeners. */
    public static boolean isLocked(Player player) {
        if (player == null) return false;
        try {
            return isStaged(player);
        } catch (Throwable t) {
            ShenanigansCore core = ShenanigansCore.getInstance();
            if (core != null) {
                core.getLogger().warning("BattlegroundLock check failed for " + player.getName()
                        + " (failing open): " + t.getMessage());
            }
            return false;
        }
    }

    /** Called on disable and on a full arena reset. */
    public static void clear() {
        STAGED.clear();
    }

    public static int stagedCount() {
        return STAGED.size();
    }

    /** Snapshot for teardown paths that must release every lock without a live Player object. */
    public static Set<UUID> staged() {
        return new java.util.HashSet<>(STAGED);
    }
}
