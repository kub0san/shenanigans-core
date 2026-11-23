package com.hizeric.shenaniganscore.battleground.arena.snapshot;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A palette/index copy of the prepared arena. Pure data: no Bukkit types, no I/O, no world access,
 * so the codec and its consumers can be unit-tested without a server.
 *
 * <p>Cells are stored per chunk in ZXY order (Y outermost, X innermost) over the region-clipped Y
 * span. Air is a normal palette entry and is written like any other state: restore must be able to
 * erase player-placed blocks, not just repaint them.</p>
 */
public final class ArenaSnapshot {

    public static final String AIR = "minecraft:air";
    public static final int CHUNK_SIZE = 16;

    /** A chunk coordinate in the snapshot's own grid, not a Minecraft region file coordinate. */
    public record ChunkPos(int x, int z) implements Comparable<ChunkPos> {
        @Override
        public int compareTo(ChunkPos other) {
            int byX = Integer.compare(x, other.x);
            return byX != 0 ? byX : Integer.compare(z, other.z);
        }
    }

    private final String mapId;
    private final String worldId;
    private final int minX, minY, minZ, maxX, maxY, maxZ;
    private final long capturedAt;
    private final long capturedBlocks;
    private final String captureEngine;
    private final List<String> palette;
    private final Map<ChunkPos, int[]> chunks;
    private final List<ChunkPos> chunkPositions;

    public ArenaSnapshot(String mapId, String worldId,
                         int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                         long capturedAt, long capturedBlocks, String captureEngine,
                         List<String> palette, Map<ChunkPos, int[]> chunks) {
        validateBounds(minX, minY, minZ, maxX, maxY, maxZ);
        this.mapId = mapId == null ? "" : mapId;
        this.worldId = worldId == null ? "" : worldId;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.capturedAt = capturedAt;
        this.capturedBlocks = capturedBlocks;
        this.captureEngine = captureEngine == null ? "" : captureEngine;
        this.palette = List.copyOf(palette);

        Map<ChunkPos, int[]> copy = new TreeMap<>();
        for (Map.Entry<ChunkPos, int[]> entry : chunks.entrySet()) {
            copy.put(entry.getKey(), Arrays.copyOf(entry.getValue(), entry.getValue().length));
        }
        this.chunks = Collections.unmodifiableMap(copy);
        this.chunkPositions = List.copyOf(copy.keySet());
    }

    public String mapId() { return mapId; }
    public String worldId() { return worldId; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int maxX() { return maxX; }
    public int maxY() { return maxY; }
    public int maxZ() { return maxZ; }
    public long capturedAt() { return capturedAt; }
    public long capturedBlocks() { return capturedBlocks; }
    public String captureEngine() { return captureEngine; }

    /** Palette entries in codec order; air is present when the arena contained air cells. */
    public List<String> palette() { return palette; }

    /** Unmodifiable view of the per-cell palette indices, used by the codec and restore engine. */
    public Map<ChunkPos, int[]> chunks() { return chunks; }

    /** Chunk coordinates in deterministic (x, then z) order — the Task 6/8 contract name. */
    public List<ChunkPos> chunkPositions() { return chunkPositions; }

    /** Defensive copy of one chunk's cells. */
    public int[] cells(ChunkPos pos) {
        int[] cells = chunks.get(pos);
        return cells == null ? null : Arrays.copyOf(cells, cells.length);
    }

    /** Number of cells a chunk must have for these region bounds (Y is clipped to the region). */
    public int cellsInChunk(ChunkPos pos) {
        return cellCount(minX, minY, minZ, maxX, maxY, maxZ, pos.x(), pos.z());
    }

    /** Rejects inverted or overflow-sized bounds before any int cell-count arithmetic. */
    static void validateBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("arena snapshot bounds are inverted");
        }
        if (totalCells(minX, minY, minZ, maxX, maxY, maxZ) > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("arena snapshot bounds exceed the int cell limit");
        }
    }

    /** Total region cells computed in long arithmetic; saturates once the int limit is passed. */
    private static long totalCells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long sx = (long) maxX - minX + 1L;
        long sy = (long) maxY - minY + 1L;
        long sz = (long) maxZ - minZ + 1L;
        if (sx <= 0 || sy <= 0 || sz <= 0) return 0L;
        if (sx > Integer.MAX_VALUE || sy > Integer.MAX_VALUE || sz > Integer.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long xy = sx * sy;
        if (xy > Integer.MAX_VALUE) return Long.MAX_VALUE;
        return xy * sz;
    }

    public static int cellCount(int minX, int minY, int minZ,
                                int maxX, int maxY, int maxZ, int chunkX, int chunkZ) {
        // Clip in long arithmetic so a hostile chunk coordinate cannot wrap the multiply.
        long baseX = (long) chunkX * CHUNK_SIZE;
        long baseZ = (long) chunkZ * CHUNK_SIZE;
        int x0 = (int) Math.max((long) minX, Math.min((long) maxX, baseX));
        int x1 = (int) Math.max((long) minX, Math.min((long) maxX, baseX + CHUNK_SIZE - 1));
        int z0 = (int) Math.max((long) minZ, Math.min((long) maxZ, baseZ));
        int z1 = (int) Math.max((long) minZ, Math.min((long) maxZ, baseZ + CHUNK_SIZE - 1));
        if (x1 < x0 || z1 < z0) return 0;
        long sy = (long) maxY - minY + 1L;
        if (sy <= 0) return 0;
        long cells = ((long) x1 - x0 + 1L) * sy * ((long) z1 - z0 + 1L);
        if (cells > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("arena snapshot chunk cell count overflows int: " + cells);
        }
        return (int) cells;
    }

    /** Target block-state string at a world block position, or {@code null} outside the snapshot. */
    public String stateAt(int x, int y, int z) {
        if (x < minX || x > maxX || y < minY || y > maxY || z < minZ || z > maxZ) return null;
        ChunkPos pos = new ChunkPos(x >> 4, z >> 4);
        int[] cells = chunks.get(pos);
        if (cells == null) return null;

        int x0 = Math.max(minX, pos.x() * CHUNK_SIZE);
        int x1 = Math.min(maxX, pos.x() * CHUNK_SIZE + CHUNK_SIZE - 1);
        int z0 = Math.max(minZ, pos.z() * CHUNK_SIZE);
        int z1 = Math.min(maxZ, pos.z() * CHUNK_SIZE + CHUNK_SIZE - 1);
        int offset = ((y - minY) * (z1 - z0 + 1) + (z - z0)) * (x1 - x0 + 1) + (x - x0);
        if (offset < 0 || offset >= cells.length) return null;
        int index = cells[offset];
        return index >= 0 && index < palette.size() ? palette.get(index) : null;
    }

    /** True only when the target state is a real block — air and cave/void air are clearable. */
    public boolean isSolidAt(int x, int y, int z) {
        return !isAir(stateAt(x, y, z));
    }

    public static boolean isAir(String state) {
        return state == null || state.isEmpty() || AIR.equals(state)
                || state.endsWith(":air") || state.endsWith("_air");
    }
}
