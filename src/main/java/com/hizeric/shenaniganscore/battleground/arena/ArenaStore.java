package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshotStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Persists arena maps, the active map selection, return point and capture metadata.
 *
 * <p>Changes:</p>
 * <ul>
 *   <li><b>Atomic save.</b> arena.yml is written to a temp file and moved into place, so a crash
 *       mid-save can no longer leave a truncated config.</li>
 *   <li><b>Prewarm tickets are tracked and released.</b> The old version added a chunk ticket for
 *       every chunk of the next map and never removed it, so each rotation leaked another map's
 *       worth of permanently loaded chunks. Old tickets are released before a new prewarm, and a
 *       late async chunk callback from an outdated prewarm is ignored.</li>
 *   <li><b>Changing the region invalidates the capture.</b> {@link #setRegion} used to keep the old
 *       captured-at/blocks values even when the bounds or world changed, leaving metadata that
 *       described a snapshot of a different area. Same bounds still keep them.</li>
 * </ul>
 */
public final class ArenaStore {

    private record Ticketed(UUID worldId, int x, int z) { }

    private final JavaPlugin plugin;
    private final File folder;
    private final File file;
    private final YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;
    private final ArenaMapRegistry maps = new ArenaMapRegistry();
    private boolean skipPrewarm;

    /** Main-thread only. */
    private final List<Ticketed> prewarmed = new ArrayList<>();
    private volatile int prewarmGeneration;

    public ArenaStore(JavaPlugin plugin, File folder) {
        this.plugin = plugin;
        this.folder = folder;
        this.file = new File(folder, "arena.yml");
        this.loader = YamlConfigurationLoader.builder().file(file).build();
    }

    /** Testing/controlled-load hook to avoid scheduling Bukkit chunk tasks. */
    public void setSkipPrewarm(boolean skipPrewarm) {
        this.skipPrewarm = skipPrewarm;
    }

    public void load() {
        try {
            root = file.exists() ? loader.load() : loader.createNode();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load battleground/arena.yml, starting fresh.", e);
            try {
                root = loader.createNode();
            } catch (Exception createFailed) {
                throw new IllegalStateException("Could not create arena config node", createFailed);
            }
        }
        maps.load(root);
        // Resolve persisted arena worlds here (enable / reload), never from the per-move
        // inWorld() path. A failed create stays retryable: ArenaRegion only marks success.
        for (String id : maps.mapIds()) {
            ArenaRegion region = maps.region(id);
            if (region != null) region.world();
        }
        prewarmNextMap();
    }

    public ArenaMapRegistry maps() { return maps; }

    public String activeMapId() { return maps.activeId(); }

    public List<String> mapIds() { return maps.mapIds(); }

    public boolean isPrepared() { return maps.activeRegion() != null; }

    public ArenaRegion region() { return maps.activeRegion(); }

    public ArenaRegion region(String mapId) { return maps.region(mapId); }

    public String worldName() { return region() == null ? null : region().worldName(); }

    public Location spawn() { return region() == null ? null : region().spawn(); }

    public File schematicFile() { return schematicFile(activeMapId()); }

    public File schematicFile(String mapId) {
        String id = normalizeMapId(mapId);
        return new File(new File(folder, id), "main.schem");
    }

    public boolean isCaptured() { return region() != null && region().isCaptured(); }

    public long capturedBlocks() { return region() == null ? 0L : region().capturedBlocks(); }

    public String captureEngine() { return region() == null ? "CLIPBOARD" : region().captureEngine(); }

    public Location returnTarget() {
        Location target = spawn();
        if (target == null) target = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0).getSpawnLocation();
        return target;
    }

    public boolean contains(Location location) { return region() != null && region().contains(location); }

    public boolean switchTo(String mapId) {
        if (mapId == null || !maps.hasMap(mapId)) return false;
        maps.setActive(mapId);
        save();
        prewarmNextMap();
        return true;
    }

    /** Fixed rotation advance. No-op when fewer than two maps exist. */
    public String rotate() {
        String id = maps.rotate();
        save();
        prewarmNextMap();
        return id;
    }

    public String nextMapId() { return maps.nextMapId(); }

    /** Keeps the next map's chunks loaded so rotation is instant. Releases the previous prewarm first. */
    public void prewarmNextMap() {
        if (skipPrewarm || plugin == null) return;
        releasePrewarm();
        final int generation = ++prewarmGeneration;

        String next = maps.nextMapId();
        if (next == null || next.isBlank() || next.equals(maps.activeId())) return;
        ArenaRegion nextRegion = maps.region(next);
        if (nextRegion == null) return;
        World world = nextRegion.world();
        if (world == null) return;

        final UUID worldId = world.getUID();
        int minCx = nextRegion.min().getBlockX() >> 4;
        int maxCx = nextRegion.max().getBlockX() >> 4;
        int minCz = nextRegion.min().getBlockZ() >> 4;
        int maxCz = nextRegion.max().getBlockZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                final int x = cx;
                final int z = cz;
                try {
                    world.getChunkAtAsync(x, z).thenAccept(chunk ->
                            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                                if (!plugin.isEnabled() || generation != prewarmGeneration) return;
                                if (!chunk.isLoaded()) return;
                                chunk.addPluginChunkTicket(plugin);
                                prewarmed.add(new Ticketed(worldId, x, z));
                            }));
                } catch (Throwable ignored) {
                    // Best-effort prewarm only.
                }
            }
        }
    }

    /** Removes every chunk ticket this store added. Safe to call on disable or before a re-prewarm. */
    public void releasePrewarm() {
        prewarmGeneration++; // invalidates callbacks still in flight
        for (Ticketed ticket : prewarmed) {
            World world = Bukkit.getWorld(ticket.worldId());
            if (world != null) world.removePluginChunkTicket(ticket.x(), ticket.z(), plugin);
        }
        prewarmed.clear();
    }

    public void setRegion(World world, BlockVector min, BlockVector max) {
        String id = activeOrDefault();
        ArenaRegion existing = maps.region(id);
        ArenaRegion updated = new ArenaRegion(world.getUID(), world.getName(), min, max, 0L, 0L, "CLIPBOARD");
        // Capture metadata only survives when the footprint is unchanged.
        boolean sameFootprint = existing != null
                && Objects.equals(existing.worldId(), world.getUID())
                && existing.min().equals(updated.min())
                && existing.max().equals(updated.max());
        if (sameFootprint) {
            updated = new ArenaRegion(world.getUID(), world.getName(), min, max,
                    existing.capturedAt(), existing.capturedBlocks(), existing.captureEngine());
        }
        if (existing != null && existing.spawn() != null) updated.spawn(existing.spawn());
        maps.put(id, updated);
        save();
    }

    public void setSpawn(Location location) {
        String id = activeOrDefault();
        ArenaRegion existing = maps.region(id);
        if (existing == null) {
            plugin.getLogger().warning("Cannot set spawn: no active arena region for map " + id);
            return;
        }
        existing.spawn(location);
        maps.put(id, existing);
        save();
    }

    public void setCaptureInfo(long blocks, String engine) {
        setCaptureInfo(activeMapId(), blocks, engine);
    }

    public void setCaptureInfo(String mapId, long blocks, String engine) {
        String id = normalizeMapId(mapId);
        ArenaRegion existing = maps.region(id);
        if (existing == null) return;
        ArenaRegion updated = new ArenaRegion(existing.worldId(), existing.worldName(),
                existing.min(), existing.max(), System.currentTimeMillis(), blocks, engine);
        updated.spawn(existing.spawn());
        maps.put(id, updated);
        save();
    }

    /** Writes arena.yml via a temp file and an atomic move. */
    public void save() {
        if (root == null) {
            try { root = loader.createNode(); } catch (Exception e) { throw new IllegalStateException(e); }
        }
        Path temp = null;
        try {
            maps.save(root);
            Files.createDirectories(folder.toPath());
            // A unique temp file prevents two concurrent saves from moving each other's bytes.
            temp = Files.createTempFile(folder.toPath(), "arena.yml-", ".tmp");
            YamlConfigurationLoader.builder().file(temp.toFile()).build().save(root);
            try {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            temp = null; // committed
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save battleground/arena.yml!", e);
        } finally {
            if (temp != null) {
                try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
            }
        }
    }

    private String activeOrDefault() {
        String id = maps.activeId();
        if (id == null || id.isBlank()) id = ArenaSnapshotStore.DEFAULT_MAP_ID;
        return id;
    }

    private static String normalizeMapId(String mapId) {
        String normalized = MapIds.normalize(mapId);
        if (normalized == null || normalized.isEmpty()) return ArenaSnapshotStore.DEFAULT_MAP_ID;
        if (!MapIds.isValid(normalized)) {
            throw new IllegalArgumentException("Invalid arena map id: " + mapId);
        }
        return normalized;
    }

    // Legacy helper used by old code paths; keep for source compatibility.
    static Map<String, Object> locationMap(Location location) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("world", location.getWorld() == null ? "" : location.getWorld().getName());
        map.put("x", location.getX());
        map.put("y", location.getY());
        map.put("z", location.getZ());
        map.put("yaw", (double) location.getYaw());
        map.put("pitch", (double) location.getPitch());
        return map;
    }
}
