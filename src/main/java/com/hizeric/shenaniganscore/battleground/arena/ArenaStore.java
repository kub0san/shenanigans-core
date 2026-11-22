package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshotStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BlockVector;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Persists arena maps, the active map selection, return point and capture metadata. */
public final class ArenaStore {

    private final JavaPlugin plugin;
    private final File folder;
    private final File file;
    private final YamlConfigurationLoader loader;
    private CommentedConfigurationNode root;
    private final ArenaMapRegistry maps = new ArenaMapRegistry();
    private boolean skipPrewarm;

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

    public void prewarmNextMap() {
        if (skipPrewarm || plugin == null) return;
        String next = maps.nextMapId();
        if (next == null || next.isBlank() || next.equals(maps.activeId())) return;
        ArenaRegion nextRegion = maps.region(next);
        if (nextRegion == null) return;
        World world = nextRegion.world();
        if (world == null) return;
        int minCx = nextRegion.min().getBlockX() >> 4;
        int maxCx = nextRegion.max().getBlockX() >> 4;
        int minCz = nextRegion.min().getBlockZ() >> 4;
        int maxCz = nextRegion.max().getBlockZ() >> 4;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                final int x = cx;
                final int z = cz;
                try {
                    world.getChunkAtAsync(x, z).thenAccept(chunk -> {
                        if (plugin == null || !plugin.isEnabled()) return;
                        Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                            if (chunk.isLoaded()) {
                                chunk.addPluginChunkTicket(plugin);
                            }
                        });
                    });
                } catch (Throwable ignored) {
                    // Best-effort prewarm only.
                }
            }
        }
    }

    public void setRegion(World world, BlockVector min, BlockVector max) {
        String id = activeOrDefault();
        ArenaRegion existing = maps.region(id);
        ArenaRegion updated = new ArenaRegion(world.getUID(), world.getName(), min, max,
                existing == null ? 0L : existing.capturedAt(),
                existing == null ? 0L : existing.capturedBlocks(),
                existing == null ? "CLIPBOARD" : existing.captureEngine());
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

    public void save() {
        if (root == null) {
            try { root = loader.createNode(); } catch (Exception e) { throw new IllegalStateException(e); }
        }
        try {
            maps.save(root);
            loader.save(root);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save battleground/arena.yml!", e);
        }
    }

    private String activeOrDefault() {
        String id = maps.activeId();
        if (id == null || id.isBlank()) id = ArenaSnapshotStore.DEFAULT_MAP_ID;
        if (!maps.hasMap(id)) {
            maps.setActive(id);
        }
        return id;
    }

    private static String normalizeMapId(String mapId) {
        if (mapId == null || mapId.isBlank()) return ArenaSnapshotStore.DEFAULT_MAP_ID;
        return mapId.trim().toLowerCase(java.util.Locale.ROOT);
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