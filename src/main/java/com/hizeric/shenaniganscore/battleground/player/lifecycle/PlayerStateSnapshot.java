package com.hizeric.shenaniganscore.battleground.player.lifecycle;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * The complete player state that staging may mutate. Captured on entry, restored on exit.
 * Using a snapshot instead of a hard-coded "safe state" means creative admins keep creative,
 * legitimate god-mode survives, and a player who was already flying keeps flying.
 */
public record PlayerStateSnapshot(
        GameMode gameMode,
        boolean allowFlight,
        boolean flying,
        boolean invulnerable,
        boolean invisible,
        boolean collidable,
        float walkSpeed,
        float flySpeed,
        int foodLevel,
        double health,
        Location location) {

    public static PlayerStateSnapshot of(Player player) {
        return new PlayerStateSnapshot(
                player.getGameMode(),
                player.getAllowFlight(),
                player.isFlying(),
                player.isInvulnerable(),
                player.isInvisible(),
                player.isCollidable(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getFoodLevel(),
                player.getHealth(),
                player.getLocation().clone());
    }

    /** Applies this snapshot back onto the player. Main thread only. */
    public void restoreTo(Player player) {
        // Order matters: gamemode first (it can reset flight), then flight flags.
        player.setGameMode(gameMode);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && flying);
        player.setInvulnerable(invulnerable);
        player.setInvisible(invisible);
        player.setCollidable(collidable);
        player.setWalkSpeed(walkSpeed);
        player.setFlySpeed(flySpeed);
        player.setFoodLevel(foodLevel);
        player.setHealth(Math.min(health, player.getMaxHealth()));
    }

    /**
     * Fallback used only when no snapshot exists (e.g. a crash-leaked player that
     * joined after a restart). Never used for a player this plugin actually staged.
     */
    public static PlayerStateSnapshot safeFallback(Player player) {
        return new PlayerStateSnapshot(
                GameMode.SURVIVAL, false, false, false, false, true,
                0.2f, 0.1f, 20, player.getMaxHealth(), player.getWorld().getSpawnLocation());
    }
}