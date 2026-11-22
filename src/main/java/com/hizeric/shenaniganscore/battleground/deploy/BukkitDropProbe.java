package com.hizeric.shenaniganscore.battleground.deploy;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.HeightMap;

/**
 * Adapts a live Bukkit {@link World} onto the pure {@link DropProbe}. A block is "air-like" when it
 * neither holds a player back nor is a liquid, matching the reference Skript's {@code deployIsAirLike}
 * (passable and not water/lava) so trees, walls and overhangs read as obstructions while open air,
 * short grass and the like do not.
 */
public final class BukkitDropProbe implements DropProbe {

    private final World world;

    public BukkitDropProbe(World world) {
        this.world = world;
    }

    @Override
    public boolean isAirLike(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return true;
        Block block = world.getBlockAt(x, y, z);
        if (block.isLiquid()) return false;
        return block.isPassable();
    }

    @Override
    public boolean isLiquid(int x, int y, int z) {
        if (y < world.getMinHeight() || y >= world.getMaxHeight()) return false;
        return world.getBlockAt(x, y, z).isLiquid();
    }

    @Override
    public int surfaceY(int x, int z) {
        // MOTION_BLOCKING keeps water/lava as the top block so surface() can flag it as liquid
        // rather than silently landing on an invisible floor underneath.
        return world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
    }
}