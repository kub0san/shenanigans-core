package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshotStore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.BlockVector;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * N-map arena registry. Persists into arena.yml as either the legacy flat format (single default
 * map) or the N-map format with {@code active} and {@code maps.<id>.*}.
 */
public final class ArenaMapRegistry {

    private final Map<String, ArenaRegion> maps = new LinkedHashMap<>();
    private String activeId = ArenaSnapshotStore.DEFAULT_MAP_ID;

    public void load(CommentedConfigurationNode root) {
        maps.clear();
        activeId = ArenaSnapshotStore.DEFAULT_MAP_ID;
        if (root == null || root.virtual()) return;

        ConfigurationNode mapsNode = root.node("maps");
        if (!mapsNode.virtual()) {
            String active = root.node("active").getString(ArenaSnapshotStore.DEFAULT_MAP_ID);
            activeId = normalize(active);
            for (Map.Entry<Object, ? extends ConfigurationNode> entry : mapsNode.childrenMap().entrySet()) {
                String id = normalize(String.valueOf(entry.getKey()));
                ArenaRegion region = readRegion(entry.getValue());
                if (region != null) maps.put(id, region);
            }
        } else {
            // Legacy flat format: treat root as the default map.
            ArenaRegion legacy = readRegion(root);
            if (legacy != null) {
                maps.put(ArenaSnapshotStore.DEFAULT_MAP_ID, legacy);
                activeId = ArenaSnapshotStore.DEFAULT_MAP_ID;
            }
        }
        if (!maps.containsKey(activeId) && !maps.isEmpty()) {
            activeId = maps.keySet().iterator().next();
        }
    }

    public void save(CommentedConfigurationNode root) throws SerializationException {
        root.node("active").set(activeId);
        // Clear legacy flat keys when writing N-map format.
        root.removeChild("world");
        root.removeChild("min");
        root.removeChild("max");
        root.removeChild("spawn");
        root.removeChild("captured-at");
        root.removeChild("captured-blocks");
        root.removeChild("capture-engine");
        root.removeChild("dive");
        root.removeChild("sky-drop");
        root.removeChild("drop-zone");
        CommentedConfigurationNode mapsNode = root.node("maps");
        // Remove stale map ids
        for (Object key : new ArrayList<>(mapsNode.childrenMap().keySet())) {
            String id = normalize(String.valueOf(key));
            if (!maps.containsKey(id)) mapsNode.removeChild(key);
        }
        for (Map.Entry<String, ArenaRegion> entry : maps.entrySet()) {
            writeRegion(mapsNode.node(entry.getKey()), entry.getValue());
        }
    }

    public String activeId() { return activeId; }

    public void setActive(String id) {
        String normalized = normalize(id);
        if (maps.containsKey(normalized)) activeId = normalized;
    }

    public ArenaRegion activeRegion() { return maps.get(activeId); }

    public ArenaRegion region(String id) { return maps.get(normalize(id)); }

    public boolean hasMap(String id) { return maps.containsKey(normalize(id)); }

    public List<String> mapIds() { return List.copyOf(maps.keySet()); }

    public void put(String id, ArenaRegion region) {
        String normalized = normalize(id);
        if (region == null) {
            maps.remove(normalized);
        } else {
            maps.put(normalized, region);
        }
        if (!maps.containsKey(activeId) && !maps.isEmpty()) activeId = maps.keySet().iterator().next();
    }

    /** Fixed rotation: advance active to next map in insertion order. No-op with <2 maps. */
    public String rotate() {
        if (maps.size() < 2) return activeId;
        List<String> ids = new ArrayList<>(maps.keySet());
        int idx = ids.indexOf(activeId);
        int next = (idx < 0 ? 0 : (idx + 1) % ids.size());
        activeId = ids.get(next);
        return activeId;
    }

    public String nextMapId() {
        if (maps.size() < 2) return activeId;
        List<String> ids = new ArrayList<>(maps.keySet());
        int idx = ids.indexOf(activeId);
        int next = (idx < 0 ? 0 : (idx + 1) % ids.size());
        return ids.get(next);
    }

    private static String normalize(String id) {
        if (id == null || id.isBlank()) return ArenaSnapshotStore.DEFAULT_MAP_ID;
        return id.trim().toLowerCase(Locale.ROOT);
    }

    private static ArenaRegion readRegion(ConfigurationNode node) {
        if (node == null || node.virtual()) return null;
        String worldName = node.node("world").getString("");
        String worldIdRaw = node.node("world-id").getString("");
        ConfigurationNode minNode = node.node("min");
        ConfigurationNode maxNode = node.node("max");
        if (worldName.isBlank() || minNode.virtual() || maxNode.virtual()) return null;

        UUID worldId = null;
        try { if (!worldIdRaw.isBlank()) worldId = UUID.fromString(worldIdRaw); } catch (Exception ignored) {}
        World world = worldId != null ? Bukkit.getWorld(worldId) : null;
        if (world == null) world = Bukkit.getWorld(worldName);
        if (world != null) {
            worldId = world.getUID();
            worldName = world.getName();
        }

        BlockVector min = new BlockVector(minNode.node("x").getInt(), minNode.node("y").getInt(), minNode.node("z").getInt());
        BlockVector max = new BlockVector(maxNode.node("x").getInt(), maxNode.node("y").getInt(), maxNode.node("z").getInt());
        long capturedAt = node.node("captured-at").getLong(0L);
        long capturedBlocks = node.node("captured-blocks").getLong(0L);
        String captureEngine = node.node("capture-engine").getString("CLIPBOARD");
        ArenaRegion region = new ArenaRegion(worldId, worldName, min, max, capturedAt, capturedBlocks, captureEngine);

        ConfigurationNode spawnNode = node.node("spawn");
        if (!spawnNode.virtual()) {
            // The return point is usually in a different world from the arena (e.g. the hub).
            // writeRegion() persists spawn.world, so honour it; legacy entries that predate
            // that field still fall back to the arena world.
            String spawnWorldName = spawnWorldName(spawnNode, world != null ? world.getName() : worldName);
            World spawnWorld = spawnWorldName == null || spawnWorldName.isBlank()
                    ? null : Bukkit.getWorld(spawnWorldName);
            if (spawnWorld != null) {
                Location spawn = new Location(spawnWorld,
                        spawnNode.node("x").getDouble(),
                        spawnNode.node("y").getDouble(),
                        spawnNode.node("z").getDouble(),
                        spawnNode.node("yaw").getFloat(),
                        spawnNode.node("pitch").getFloat());
                region.spawn(spawn);
            }
        }
        return region;
    }

    /** Pure world-name resolution for the persisted return point; no Bukkit calls. */
    static String spawnWorldName(ConfigurationNode spawnNode, String arenaWorldName) {
        String configured = spawnNode.node("world").getString("");
        return configured == null || configured.isBlank() ? arenaWorldName : configured;
    }

    private static void writeRegion(CommentedConfigurationNode node, ArenaRegion region) throws SerializationException {
        node.node("world").set(region.worldName());
        node.node("world-id").set(region.worldId() == null ? "" : region.worldId().toString());
        node.node("min").node("x").set(region.min().getBlockX());
        node.node("min").node("y").set(region.min().getBlockY());
        node.node("min").node("z").set(region.min().getBlockZ());
        node.node("max").node("x").set(region.max().getBlockX());
        node.node("max").node("y").set(region.max().getBlockY());
        node.node("max").node("z").set(region.max().getBlockZ());
        node.node("captured-at").set(region.capturedAt());
        node.node("captured-blocks").set(region.capturedBlocks());
        node.node("capture-engine").set(region.captureEngine());
        Location spawn = region.spawn();
        if (spawn != null) {
            node.node("spawn").node("world").set(spawn.getWorld() == null ? region.worldName() : spawn.getWorld().getName());
            node.node("spawn").node("x").set(spawn.getX());
            node.node("spawn").node("y").set(spawn.getY());
            node.node("spawn").node("z").set(spawn.getZ());
            node.node("spawn").node("yaw").set((double) spawn.getYaw());
            node.node("spawn").node("pitch").set((double) spawn.getPitch());
        }
    }
}
