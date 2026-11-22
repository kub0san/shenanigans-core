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

/**
 * Definition of the arena footprint and its return point.
 *
 * <p>Bounds are normalized on construction (a hand-edited arena.yml with min and max swapped no
 * longer makes every containment check fail) and copied, so later changes to the vectors passed in
 * cannot move the arena. {@link #min()} and {@link #max()} hand back the internal vectors because
 * they sit on hot paths; treat them as read-only.</p>
 */
public final class ArenaRegion {

    private final UUID worldId;
    private final String worldName;
    private final BlockVector min;
    private final BlockVector max;
    private final long capturedAt;
    private final long capturedBlocks;
    private final String captureEngine;
    private volatile Location spawn;
    private final AtomicBoolean worldLoadAttempted = new AtomicBoolean(false);

    public ArenaRegion(UUID worldId, String worldName, BlockVector min, BlockVector max,
                       long capturedAt, long capturedBlocks, String captureEngine) {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        this.worldId = worldId;
        this.worldName = worldName;
        this.min = new BlockVector(
                Math.min(min.getBlockX(), max.getBlockX()),
                Math.min(min.getBlockY(), max.getBlockY()),
                Math.min(min.getBlockZ(), max.getBlockZ()));
        this.max = new BlockVector(
                Math.max(min.getBlockX(), max.getBlockX()),
                Math.max(min.getBlockY(), max.getBlockY()),
                Math.max(min.getBlockZ(), max.getBlockZ()));
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

    /** A copy, so callers cannot move the stored return point by mutating it. */
    public Location spawn() {
        Location current = spawn;
        return current == null ? null : current.clone();
    }

    public ArenaRegion spawn(Location spawn) {
        this.spawn = spawn == null ? null : spawn.clone();
        return this;
    }

    public boolean isCaptured() { return capturedAt > 0L; }

    public World world() {
        World world = worldId == null ? null : Bukkit.getWorld(worldId);
        if (world != null) return world;
        if (worldName == null || worldName.isBlank()) return null;
        world = Bukkit.getWorld(worldName);
        if (world != null) return world;
        // Lazy fallback: try to load the persisted world from disk once, so a non-default arena
        // world that was not auto-loaded on restart still resolves without a manual visit.
        if (worldLoadAttempted.getAndSet(true)) return null;
        File folder = new File(Bukkit.getWorldContainer(), worldName);
        if (!folder.isDirectory()) return null;
        try {
            return Bukkit.createWorld(new WorldCreator(worldName));
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * True when {@code other} is the arena's world. Compares the UUID first, which avoids a world
     * lookup on the common path (every player move, every block in an explosion).
     */
    public boolean inWorld(World other) {
        if (other == null) return false;
        if (worldId != null && worldId.equals(other.getUID())) return true;
        World mine = world();
        return mine != null && mine.equals(other);
    }

    /** Pure coordinate check, no world involved. */
    public boolean containsBlock(int x, int y, int z) {
        return x >= min.getBlockX() && x <= max.getBlockX()
                && y >= min.getBlockY() && y <= max.getBlockY()
                && z >= min.getBlockZ() && z <= max.getBlockZ();
    }

    /** World plus coordinates; the cheap form for per-block loops. */
    public boolean containsBlock(World world, int x, int y, int z) {
        return containsBlock(x, y, z) && inWorld(world);
    }

    /** Contains check ignores Y so spawning above/below the map still counts as "in the arena". */
    public boolean containsHorizontal(Location location) {
        if (location == null) return false;
        int x = location.getBlockX();
        int z = location.getBlockZ();
        if (x < min.getBlockX() || x > max.getBlockX()
                || z < min.getBlockZ() || z > max.getBlockZ()) {
            return false;
        }
        return inWorld(location.getWorld());
    }

    /** Full 3D containment, used by the entity sweeper. */
    public boolean contains(Location location) {
        if (location == null) return false;
        int y = location.getBlockY();
        if (y < min.getBlockY() || y > max.getBlockY()) return false;
        return containsHorizontal(location);
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
