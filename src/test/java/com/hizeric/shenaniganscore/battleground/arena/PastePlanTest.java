package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snapshot-equivalent of the old paste plan invariant: air is part of the captured snapshot,
 * so restoring it erases blocks placed during the round (ignoreAirBlocks == false equivalent).
 */
class PastePlanTest {

    @Test
    void snapshotIncludesAirCellsSoRestoreCanErasePlayerBlocks() {
        List<String> palette = List.of(ArenaSnapshot.AIR, "minecraft:stone");
        int[] cells = new int[] {
                1, // (0,64,0) stone
                0  // (0,65,0) air
        };
        Map<ChunkPos, int[]> chunks = new LinkedHashMap<>();
        chunks.put(new ChunkPos(0, 0), cells);
        ArenaSnapshot snapshot = new ArenaSnapshot("ffa", "world",
                0, 64, 0, 0, 65, 0,
                1L, 2L, "SNAPSHOT", palette, chunks);

        assertEquals("minecraft:stone", snapshot.stateAt(0, 64, 0));
        assertEquals(ArenaSnapshot.AIR, snapshot.stateAt(0, 65, 0),
                "air must be stored in the snapshot, not ignored");
        assertTrue(ArenaSnapshot.isAir(snapshot.stateAt(0, 65, 0)));
        assertFalse(snapshot.isSolidAt(0, 65, 0),
                "air cells are clearable targets so player-placed blocks can be erased");
        assertTrue(snapshot.isSolidAt(0, 64, 0));
    }
}
