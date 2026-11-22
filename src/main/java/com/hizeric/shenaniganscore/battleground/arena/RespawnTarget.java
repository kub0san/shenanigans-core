package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Resolves the destination of a respawn from the return point configured with {@code /bg setspawn}
 * and the world's spawn. The configured point wins; the world spawn is the fallback when none is
 * set. Kept pure so the decision can be tested without a running server.
 */
public final class RespawnTarget {

    private RespawnTarget() { }

    /** Returns {@code configured} when non-null, otherwise {@code worldSpawn}. */
    public static Location resolve(Location configured, Location worldSpawn) {
        return configured != null ? configured : worldSpawn;
    }

    /**
     * Same precedence, but never returns a point inside the arena. If the configured point is
     * inside, the world spawn is tried; if both are inside, a point just outside the arena's
     * minimum corner is used. This keeps a misconfigured {@code /bg setspawn} from trapping a
     * returning player in the protected region.
     */
    public static Location resolve(Location configured, Location worldSpawn, ArenaRegion region) {
        Location choice = resolve(configured, worldSpawn);
        if (choice == null || region == null) return choice;
        if (!inside(region, choice)) return choice;

        Location other = choice == configured ? worldSpawn : configured;
        if (other != null && !inside(region, other)) return other;

        World world = choice.getWorld();
        return new Location(world,
                region.min().getBlockX() - 1.0,
                choice.getY(),
                region.min().getBlockZ() - 1.0);
    }

    /** Pure containment check: world name when both sides know it, then the region's bounds. */
    private static boolean inside(ArenaRegion region, Location location) {
        if (location == null) return false;
        String name = location.getWorld() == null ? null : location.getWorld().getName();
        if (name != null && !name.equals(region.worldName())) return false;
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= region.min().getBlockX() && x <= region.max().getBlockX()
                && y >= region.min().getBlockY() && y <= region.max().getBlockY()
                && z >= region.min().getBlockZ() && z <= region.max().getBlockZ();
    }
}
