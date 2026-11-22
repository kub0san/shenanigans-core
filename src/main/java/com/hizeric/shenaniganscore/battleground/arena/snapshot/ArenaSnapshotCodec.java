package com.hizeric.shenaniganscore.battleground.arena.snapshot;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure binary codec for {@link ArenaSnapshot}. No Bukkit, no files: encoding and decoding are
 * byte-for-byte deterministic so two admins capturing the same arena get the same bytes.
 *
 * <pre>
 * SCARENA1 | version | mapId | worldId | min/max (6 ints) | capturedAt/blocks | engine
 * palette count + sorted state strings
 * chunk count + per chunk:
 *   v1: chunkX, chunkZ, cellCount, cellCount x int palette index
 *   v2: chunkX, chunkZ, cellCount, runCount, runCount x (varint index, varint length)
 * </pre>
 *
 * <p>Version 2 run-length encodes each chunk. Arenas are mostly air and uniform terrain, so
 * files shrink dramatically (often 20x or more) and load faster. Version 1 files still decode, and
 * the next capture simply writes version 2.</p>
 */
public final class ArenaSnapshotCodec {

    public static final String MAGIC = "SCARENA1";
    public static final int FORMAT_VERSION = 2;
    private static final int LEGACY_VERSION = 1;
    private static final int MAX_PALETTES = 100_000;
    private static final int MAX_CHUNKS = 1_000_000;

    private ArenaSnapshotCodec() { }

    /** Serialises a snapshot. Throws only unchecked exceptions; this never touches the disk. */
    public static byte[] encode(ArenaSnapshot snapshot) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(buffer)) {
                out.writeUTF(MAGIC);
                out.writeInt(FORMAT_VERSION);
                out.writeUTF(snapshot.mapId());
                out.writeUTF(snapshot.worldId());
                out.writeInt(snapshot.minX());
                out.writeInt(snapshot.minY());
                out.writeInt(snapshot.minZ());
                out.writeInt(snapshot.maxX());
                out.writeInt(snapshot.maxY());
                out.writeInt(snapshot.maxZ());
                out.writeLong(snapshot.capturedAt());
                out.writeLong(snapshot.capturedBlocks());
                out.writeUTF(snapshot.captureEngine());

                // Canonical palette order makes the bytes independent of encounter order.
                List<String> sorted = new ArrayList<>(snapshot.palette());
                Collections.sort(sorted);
                Map<String, Integer> canonical = new HashMap<>();
                for (int i = 0; i < sorted.size(); i++) canonical.put(sorted.get(i), i);
                int[] remap = new int[snapshot.palette().size()];
                for (int i = 0; i < remap.length; i++) {
                    remap[i] = canonical.get(snapshot.palette().get(i));
                }

                out.writeInt(sorted.size());
                for (String state : sorted) out.writeUTF(state);

                out.writeInt(snapshot.chunks().size());
                for (Map.Entry<ChunkPos, int[]> entry : new TreeMap<>(snapshot.chunks()).entrySet()) {
                    ChunkPos pos = entry.getKey();
                    int[] cells = entry.getValue();
                    int expected = snapshot.cellsInChunk(pos);
                    if (cells.length != expected) {
                        throw new IOException("chunk " + pos + " has " + cells.length
                                + " cells, expected " + expected);
                    }
                    out.writeInt(pos.x());
                    out.writeInt(pos.z());
                    out.writeInt(cells.length);
                    writeRuns(out, pos, cells, remap);
                }
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode the arena snapshot", e);
        }
    }

    private static void writeRuns(DataOutputStream out, ChunkPos pos, int[] cells, int[] remap)
            throws IOException {
        ByteArrayOutputStream runBytes = new ByteArrayOutputStream();
        DataOutputStream runs = new DataOutputStream(runBytes);
        int runCount = 0;
        int i = 0;
        while (i < cells.length) {
            int cell = cells[i];
            if (cell < 0 || cell >= remap.length) {
                throw new IOException("chunk " + pos + " has an out-of-range palette index");
            }
            int j = i + 1;
            while (j < cells.length && cells[j] == cell) j++;
            writeVarInt(runs, remap[cell]);
            writeVarInt(runs, j - i);
            runCount++;
            i = j;
        }
        out.writeInt(runCount);
        runBytes.writeTo(out);
    }

    /** Reads back a file written by {@link #encode(ArenaSnapshot)} (version 1 or 2). */
    public static ArenaSnapshot decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0) throw new IOException("arena snapshot file is empty");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            String magic = in.readUTF();
            if (!MAGIC.equals(magic)) throw new IOException("arena snapshot file has an unknown header");
            int version = in.readInt();
            if (version != LEGACY_VERSION && version != FORMAT_VERSION) {
                throw new IOException("unsupported arena snapshot version: " + version);
            }
            String mapId = in.readUTF();
            String worldId = in.readUTF();
            int minX = in.readInt(), minY = in.readInt(), minZ = in.readInt();
            int maxX = in.readInt(), maxY = in.readInt(), maxZ = in.readInt();
            if (minX > maxX || minY > maxY || minZ > maxZ) {
                throw new IOException("arena snapshot bounds are inverted");
            }
            long capturedAt = in.readLong();
            long capturedBlocks = in.readLong();
            String engine = in.readUTF();

            int paletteSize = in.readInt();
            if (paletteSize < 0 || paletteSize > MAX_PALETTES) {
                throw new IOException("arena snapshot palette size is implausible: " + paletteSize);
            }
            List<String> palette = new ArrayList<>(paletteSize);
            for (int i = 0; i < paletteSize; i++) palette.add(in.readUTF());

            int chunkCount = in.readInt();
            if (chunkCount < 0 || chunkCount > MAX_CHUNKS) {
                throw new IOException("arena snapshot chunk count is implausible: " + chunkCount);
            }
            Map<ChunkPos, int[]> chunks = new TreeMap<>();
            for (int i = 0; i < chunkCount; i++) {
                int chunkX = in.readInt();
                int chunkZ = in.readInt();
                ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                int cells = in.readInt();
                int expected = ArenaSnapshot.cellCount(minX, minY, minZ, maxX, maxY, maxZ, chunkX, chunkZ);
                if (cells != expected) {
                    throw new IOException("chunk " + pos + " has " + cells + " cells, expected " + expected);
                }
                int[] indices = version == LEGACY_VERSION
                        ? readLegacyCells(in, pos, cells, paletteSize)
                        : readRuns(in, pos, cells, paletteSize);
                if (chunks.put(pos, indices) != null) {
                    throw new IOException("duplicate arena snapshot chunk: " + pos);
                }
            }
            return new ArenaSnapshot(mapId, worldId, minX, minY, minZ, maxX, maxY, maxZ,
                    capturedAt, capturedBlocks, engine, palette, chunks);
        }
    }

    private static int[] readLegacyCells(DataInputStream in, ChunkPos pos, int cells, int paletteSize)
            throws IOException {
        int[] indices = new int[cells];
        for (int j = 0; j < cells; j++) {
            int index = in.readInt();
            if (index < 0 || index >= paletteSize) {
                throw new IOException("chunk " + pos + " has an out-of-range palette index");
            }
            indices[j] = index;
        }
        return indices;
    }

    private static int[] readRuns(DataInputStream in, ChunkPos pos, int cells, int paletteSize)
            throws IOException {
        int runCount = in.readInt();
        if (runCount < 0 || runCount > cells) {
            throw new IOException("chunk " + pos + " has an implausible run count: " + runCount);
        }
        int[] indices = new int[cells];
        int filled = 0;
        for (int run = 0; run < runCount; run++) {
            int index = readVarInt(in);
            int length = readVarInt(in);
            if (index < 0 || index >= paletteSize) {
                throw new IOException("chunk " + pos + " has an out-of-range palette index");
            }
            if (length <= 0 || length > cells - filled) {
                throw new IOException("chunk " + pos + " has a run that overflows the chunk");
            }
            Arrays.fill(indices, filled, filled + length, index);
            filled += length;
        }
        if (filled != cells) {
            throw new IOException("chunk " + pos + " runs cover " + filled + " of " + cells + " cells");
        }
        return indices;
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int result = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) return result;
            shift += 7;
            if (shift > 28) throw new IOException("varint is too long");
        }
    }
}
