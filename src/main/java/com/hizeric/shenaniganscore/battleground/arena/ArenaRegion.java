package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.util.BlockVector;

import java.io.File;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable definition of the arena footprint and its return point. */
public final class ArenaRegion {

    private final UUID worldId;
    private final String worldName;
    private final BlockVector min;
    private final BlockVector max;
    private final long capturedAt;
    private final long capturedBlocks;
    private final String captureEngine;
    private Location spawn;

    public ArenaRegion(UUID worldId, String worldName, BlockVector min, BlockVector max,
                       long capturedAt, long capturedBlocks, String captureEngine) {
        this.worldId = worldId;
        this.worldName = worldName;
        this.min = Objects.requireNonNull(min, "min");
        this.max = Objects.requireNonNull(max, "max");
        this.capturedAt = capturedAt;
        this.capturedBlocks = capturedBlocks;
        this.captureEngine = captureEngine;
    }

    public UUID worldId() { return worldId; }
    public String worldName() { return worldName; }
    public BlockVector min() { return min; }
    public BlockVector max() { return max; }
    public long capturedAt() { return capturedAt; }
    public long capturedBlocks() { return capturedBlocks; }
    public String captureEngine() { return captureEngine; }

    public Location spawn() { return spawn; }
    public ArenaRegion spawn(Location spawn) { this.spawn = spawn; return this; }

    public boolean isCaptured() { return capturedAt > 0L; }

    private final AtomicBoolean worldLoadAttempted = new AtomicBoolean(false);

    public World world() {
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world != null) return world;
        world = Bukkit.getWorld(worldName);
        if (world != null) return world;
        // Lazy fallback: try to load the persisted world from disk once, so a non-default arena
        // world that was not auto-loaded on restart still resolves without a manual visit.
        if (worldName == null || worldName.isBlank() || worldLoadAttempted.getAndSet(true)) return null;
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (!folder.isDirectory()) return null;
        try {
            return Bukkit.createWorld(new WorldCreator(worldName));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Contains check ignores Y so spawning above/below the map still counts as "in the arena". */
    public boolean containsHorizontal(Location location) {
        if (location == null || !location.getWorld().equals(world())) return false;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return x >= min.getBlockX() && x <= max.getBlockX()
                && z >= min.getBlockZ() && z <= max.getBlockZ();
    }

    /** Full 3D containment, used by the entity sweeper. */
    public boolean contains(Location location) {
        if (!containsHorizontal(location)) return false;
        int y = location.getBlockY();
        return y >= min.getBlockY() && y <= max.getBlockY();
    }

    public Location center() {
        World world = world();
        if (world == null) return null;
        return new Location(world,
                (min.getBlockX() + max.getBlockX()) / 2.0,
                (min.getBlockY() + max.getBlockY()) / 2.0,
                (min.getBlockZ() + max.getBlockZ()) / 2.0);
    }

    public int sizeX() { return max.getBlockX() - min.getBlockX() + 1; }
    public int sizeY() { return max.getBlockY() - min.getBlockY() + 1; }
    public int sizeZ() { return max.getBlockZ() - min.getBlockZ() + 1; }
}
