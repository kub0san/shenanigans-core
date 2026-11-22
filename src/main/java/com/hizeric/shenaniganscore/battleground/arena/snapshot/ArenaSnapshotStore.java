package com.hizeric.shenaniganscore.battleground.arena.snapshot;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.MapIds;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * Disk owner for per-map snapshots: {@code battleground/arena/<mapId>/snapshot.bin}.
 * Only an explicit admin capture writes a file; loading a missing snapshot warns and returns null
 * so a dirty world can never silently become the baseline.
 *
 * <p>Improvements: capture reads a {@link ChunkSnapshot} (one bulk copy per chunk instead of a live
 * block lookup per cell), converts each distinct {@link BlockData} to its string once instead of
 * once per cell, and saves with temp file + fsync + atomic move so a crash mid-save can never
 * corrupt the existing baseline.</p>
 */
public final class ArenaSnapshotStore {

    public static final String DEFAULT_MAP_ID = "default";
    /** Refuse a snapshot larger than this before any byte is read into memory. */
    static final long MAX_SNAPSHOT_BYTES = 64L * 1024L * 1024L;
    private static final String TMP_SUFFIX = ".tmp";

    private final Path arenaRoot;
    private final Logger logger;

    public ArenaSnapshotStore(Path arenaRoot, Logger logger) {
        this.arenaRoot = Objects.requireNonNull(arenaRoot, "arenaRoot");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public Path snapshotPath(String mapId) {
        String normalized = MapIds.normalize(mapId);
        if (!MapIds.isValid(normalized)) {
            throw new IllegalArgumentException("unsafe arena map id: " + mapId);
        }
        return arenaRoot.resolve(normalized).resolve("snapshot.bin");
    }

    public boolean exists(String mapId) {
        return Files.isRegularFile(snapshotPath(mapId));
    }

    /**
     * Loads the active map's snapshot. A missing file is a warning, not a capture: the restore
     * caller skips its work when this returns {@code null}.
     */
    public ArenaSnapshot load(String mapId) throws IOException {
        Path file = snapshotPath(mapId);
        if (!Files.isRegularFile(file)) {
            logger.warning("No arena snapshot for map '" + mapId + "' at " + file
                    + " — restore skipped until an admin runs /bg map capture.");
            return null;
        }
        long size = Files.size(file);
        if (size > MAX_SNAPSHOT_BYTES) {
            throw new IOException("arena snapshot file is too large (" + size + " bytes, max "
                    + MAX_SNAPSHOT_BYTES + "): " + file);
        }
        return ArenaSnapshotCodec.decode(Files.readAllBytes(file));
    }

    /** Atomic, durable write: temp file, fsync, then replace the target in one move. */
    public void save(String mapId, ArenaSnapshot snapshot) throws IOException {
        Path target = snapshotPath(mapId);
        Files.createDirectories(target.getParent());
        Path temp = target.resolveSibling(target.getFileName() + TMP_SUFFIX);
        byte[] bytes = ArenaSnapshotCodec.encode(snapshot);
        try {
            try (FileChannel channel = FileChannel.open(temp,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /**
     * Captures the prepared region into the active map's snapshot <b>and saves it</b>. Callers must
     * stop domains and sweep entities first; this method only walks blocks and encodes.
     *
     * <p>Chunk access is synchronous: capture is an explicit admin action, so a short stall while
     * arena chunks load is acceptable. The warning makes the cost visible in the console.</p>
     */
    public ArenaSnapshot capture(String mapId, ArenaRegion region) throws IOException {
        World world = region == null ? null : region.world();
        if (world == null) {
            throw new IOException("arena world is not loaded; cannot capture map " + mapId);
        }
        logger.warning("Capturing arena snapshot for map '" + mapId
                + "': chunk loads are synchronous; keep the arena world loaded.");

        int minX = region.min().getBlockX(), minY = region.min().getBlockY(), minZ = region.min().getBlockZ();
        int maxX = region.max().getBlockX(), maxY = region.max().getBlockY(), maxZ = region.max().getBlockZ();

        // BlockData -> palette index. getAsString() only runs for the first sight of each state.
        Map<BlockData, Integer> lookup = new HashMap<>();
        List<String> states = new ArrayList<>();
        Map<ChunkPos, int[]> chunks = new TreeMap<>();
        long capturedBlocks = 0L;

        for (int chunkX = minX >> 4; chunkX <= maxX >> 4; chunkX++) {
            for (int chunkZ = minZ >> 4; chunkZ <= maxZ >> 4; chunkZ++) {
                Chunk loaded = world.getChunkAt(chunkX, chunkZ);
                int x0 = Math.max(minX, chunkX * ArenaSnapshot.CHUNK_SIZE);
                int x1 = Math.min(maxX, chunkX * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
                int z0 = Math.max(minZ, chunkZ * ArenaSnapshot.CHUNK_SIZE);
                int z1 = Math.min(maxZ, chunkZ * ArenaSnapshot.CHUNK_SIZE + ArenaSnapshot.CHUNK_SIZE - 1);
                // BlockState#getAsString() cannot carry block-entity NBT. Refuse the capture
                // rather than silently dropping chest/sign/etc. data from the baseline.
                for (BlockState state : loaded.getTileEntities()) {
                    int bx = state.getX(), by = state.getY(), bz = state.getZ();
                    if (bx >= x0 && bx <= x1 && by >= minY && by <= maxY && bz >= z0 && bz <= z1) {
                        throw new IllegalArgumentException("Cannot capture arena map '" + mapId
                                + "': block entity " + state.getType() + " at "
                                + bx + "," + by + "," + bz + " is inside the region. Snapshots"
                                + " store block states only and would discard its NBT; remove"
                                + " block entities and capture again.");
                    }
                }
                ChunkSnapshot chunk = loaded.getChunkSnapshot(false, false, false);
                int[] cells = new int[ArenaSnapshot.cellCount(minX, minY, minZ, maxX, maxY, maxZ,
                        chunkX, chunkZ)];
                int offset = 0;
                // ZXY order: Y outermost, X innermost.
                for (int y = minY; y <= maxY; y++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int x = x0; x <= x1; x++) {
                            BlockData data = chunk.getBlockData(x & 15, y, z & 15);
                            Integer index = lookup.get(data);
                            if (index == null) {
                                index = states.size();
                                states.add(data.getAsString());
                                lookup.put(data, index);
                            }
                            cells[offset++] = index;
                        }
                    }
                }
                chunks.put(new ChunkPos(chunkX, chunkZ), cells);
                capturedBlocks += cells.length;
            }
        }

        ArenaSnapshot snapshot = new ArenaSnapshot(mapId, world.getUID().toString(),
                minX, minY, minZ, maxX, maxY, maxZ,
                System.currentTimeMillis(), capturedBlocks, "SNAPSHOT", states, chunks);
        save(mapId, snapshot);
        return snapshot;
    }
}
