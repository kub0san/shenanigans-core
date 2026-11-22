package com.hizeric.shenaniganscore.battleground.arena.snapshot;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure codec tests: no server, no Bukkit, no files. */
class ArenaSnapshotCodecTest {

    private static ArenaSnapshot snapshot(int minX, int minY, int minZ,
                                          int maxX, int maxY, int maxZ,
                                          List<String> palette, Map<ChunkPos, int[]> chunks) {
        return new ArenaSnapshot("ffa", "world", minX, minY, minZ, maxX, maxY, maxZ,
                1234L, 5678L, "TEST", palette, chunks);
    }

    @Test
    void paletteIndexRoundTripPreservesStatesAndMetadata() throws Exception {
        int[] cells = new int[8];
        cells[0] = 0; // stone
        cells[7] = 1; // air
        ArenaSnapshot original = snapshot(0, 0, 0, 1, 1, 1,
                List.of("minecraft:stone", "minecraft:air"), Map.of(new ChunkPos(0, 0), cells));

        ArenaSnapshot decoded = ArenaSnapshotCodec.decode(ArenaSnapshotCodec.encode(original));

        assertEquals("ffa", decoded.mapId());
        assertEquals("world", decoded.worldId());
        assertEquals(0, decoded.minX());
        assertEquals(1, decoded.maxY());
        assertEquals(1234L, decoded.capturedAt());
        assertEquals(5678L, decoded.capturedBlocks());
        assertEquals("TEST", decoded.captureEngine());
        assertEquals(List.of("minecraft:air", "minecraft:stone"), decoded.palette());
        // Raw indices are canonicalised to sorted palette order; the states they resolve to are identical.
        assertEquals(cells.length, decoded.cells(new ChunkPos(0, 0)).length);
        assertEquals("minecraft:stone", decoded.stateAt(0, 0, 0));
        assertEquals("minecraft:air", decoded.stateAt(1, 1, 1));
        assertNull(decoded.stateAt(2, 0, 0), "outside the captured region has no state");
    }

    @Test
    void airIsStoredInThePaletteAndDecodedAsNonSolid() throws Exception {
        int[] cells = {0, 1, 2};
        ArenaSnapshot original = snapshot(0, 60, 0, 0, 62, 0,
                List.of("minecraft:air", "minecraft:stone", "minecraft:cave_air"),
                Map.of(new ChunkPos(0, 0), cells));

        ArenaSnapshot decoded = ArenaSnapshotCodec.decode(ArenaSnapshotCodec.encode(original));

        assertTrue(decoded.palette().contains("minecraft:air"), "air must be stored, not skipped");
        assertEquals("minecraft:air", decoded.stateAt(0, 60, 0));
        assertEquals("minecraft:stone", decoded.stateAt(0, 61, 0));
        assertEquals("minecraft:cave_air", decoded.stateAt(0, 62, 0));
        assertFalse(decoded.isSolidAt(0, 60, 0));
        assertTrue(decoded.isSolidAt(0, 61, 0));
        assertFalse(decoded.isSolidAt(0, 62, 0));
    }

    @Test
    void clippedYSpanUsesRegionBoundsForCellCountAndZxyOffsets() throws Exception {
        int[] cells = {0, 1, 0};
        ArenaSnapshot original = snapshot(4, 60, 9, 4, 62, 9,
                List.of("minecraft:air", "minecraft:stone"), Map.of(new ChunkPos(0, 0), cells));

        ArenaSnapshot decoded = ArenaSnapshotCodec.decode(ArenaSnapshotCodec.encode(original));

        assertEquals(3, decoded.cells(new ChunkPos(0, 0)).length, "3 clipped Y layers, one X/Z cell");
        assertEquals("minecraft:air", decoded.stateAt(4, 60, 9));
        assertEquals("minecraft:stone", decoded.stateAt(4, 61, 9));
        assertEquals("minecraft:air", decoded.stateAt(4, 62, 9));
        assertNull(decoded.stateAt(4, 63, 9), "above the clipped span is outside the snapshot");
    }

    @Test
    void chunksAreSortedAndBytesAreDeterministicAcrossInputOrder() throws Exception {
        Map<ChunkPos, int[]> scrambled = new LinkedHashMap<>();
        scrambled.put(new ChunkPos(1, 1), new int[256]);
        scrambled.put(new ChunkPos(0, 1), new int[256]);
        scrambled.put(new ChunkPos(1, 0), new int[256]);
        scrambled.put(new ChunkPos(0, 0), new int[256]);
        ArenaSnapshot original = snapshot(0, 0, 0, 31, 0, 31,
                List.of("minecraft:stone", "minecraft:air"), scrambled);

        byte[] encoded = ArenaSnapshotCodec.encode(original);
        ArenaSnapshot decoded = ArenaSnapshotCodec.decode(encoded);

        assertEquals(List.of(new ChunkPos(0, 0), new ChunkPos(0, 1),
                        new ChunkPos(1, 0), new ChunkPos(1, 1)),
                decoded.chunkPositions());

        // Same states, opposite palette order: canonical encoding must agree byte-for-byte.
        int[] swapped = new int[256];
        java.util.Arrays.fill(swapped, 1); // stone is index 1 in this palette
        Map<ChunkPos, int[]> oneChunk = Map.of(new ChunkPos(0, 0), swapped);
        ArenaSnapshot reordered = snapshot(0, 0, 0, 31, 0, 31,
                List.of("minecraft:air", "minecraft:stone"), oneChunk);
        int[] originalCells = new int[256]; // stone is index 0 in the original palette
        ArenaSnapshot first = snapshot(0, 0, 0, 31, 0, 31,
                List.of("minecraft:stone", "minecraft:air"), Map.of(new ChunkPos(0, 0), originalCells));

        assertArrayEquals(ArenaSnapshotCodec.encode(first), ArenaSnapshotCodec.encode(reordered));
        assertEquals("minecraft:stone", decoded.stateAt(0, 0, 0));
    }

    @Test
    void decodeRejectsCorruptHeadersAndCellCounts() {
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> ArenaSnapshotCodec.decode(new byte[0]));
        byte[] good = ArenaSnapshotCodec.encode(snapshot(0, 0, 0, 0, 0, 0,
                List.of("minecraft:air"), Map.of(new ChunkPos(0, 0), new int[]{0})));
        good[0] ^= 0x7F;
        org.junit.jupiter.api.Assertions.assertThrows(java.io.IOException.class,
                () -> ArenaSnapshotCodec.decode(good));
    }
}
