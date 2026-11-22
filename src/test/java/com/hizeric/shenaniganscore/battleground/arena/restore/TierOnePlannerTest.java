package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure tier-1 selection: no server, no Bukkit, no files. */
class TierOnePlannerTest {

    private static final int MIN_Y = 60;
    private static final int MAX_Y = 100;
    private static final int SURFACE_Y = 61;

    /** 9x9 arena, stone surface at y=61, air from y=62 up. Palette: 0=air, 1=stone. */
    private static ArenaSnapshot snapshot() {
        int[] cells = new int[ArenaSnapshot.cellCount(0, MIN_Y, 0, 8, MAX_Y, 8, 0, 0)];
        for (int y = MIN_Y; y <= SURFACE_Y; y++) {
            for (int z = 0; z <= 8; z++) {
                for (int x = 0; x <= 8; x++) {
                    cells[((y - MIN_Y) * 9 + z) * 9 + x] = 1;
                }
            }
        }
        return new ArenaSnapshot("ffa", "world", 0, MIN_Y, 0, 8, MAX_Y, 8,
                1234L, 81L, "TEST", List.of("minecraft:air", "minecraft:stone"),
                Map.of(new ChunkPos(0, 0), cells));
    }

    @Test
    void clearanceSpanMatchesDeployHeightPlusSpawnClearance() {
        // max(DEPLOY_CLEARANCE_HEIGHT=10, DEPLOY_DROP_HEIGHT=20 + DEPLOY_SPAWN_CLEARANCE=3) = 23
        assertEquals(23, TierOnePlanner.CLEARANCE_SPAN);
    }

    @Test
    void selectsSurfaceAndClearanceOnlyOnSampledDropColumns() {
        Set<Long> diff = Set.of(
                BlockKey.pack(0, 61, 0),  // surface of a sampled column
                BlockKey.pack(0, 65, 0),  // non-baseline block inside the clearance span
                BlockKey.pack(0, 84, 0),  // exactly 23 above the surface
                BlockKey.pack(3, 65, 3),  // sampled column (stride 3 in both axes)
                BlockKey.pack(6, 70, 6),  // sampled column
                BlockKey.pack(0, 85, 0),  // 24 above the surface: outside the span
                BlockKey.pack(1, 65, 0),  // off-grid x: not a sampled column
                BlockKey.pack(0, 65, 1),  // off-grid z: not a sampled column
                BlockKey.pack(0, 60, 0),  // below the surface: not the surface block
                BlockKey.pack(9, 65, 0)); // outside the snapshot

        Set<Long> selected = TierOnePlanner.select(diff, snapshot());

        assertEquals(Set.of(
                BlockKey.pack(0, 61, 0),
                BlockKey.pack(0, 65, 0),
                BlockKey.pack(0, 84, 0),
                BlockKey.pack(3, 65, 3),
                BlockKey.pack(6, 70, 6)), selected);
    }

    @Test
    void excludesWallsAndSpawnHeightBecauseThisModeHasNeither() {
        // Off-grid columns (what a wall would occupy) and blocks above the 23-block drop span
        // (where a spawn platform used to sit) must never be tier-1 essentials.
        Set<Long> diff = Set.of(
                BlockKey.pack(1, 62, 0),  // wall-like off-grid column
                BlockKey.pack(0, 62, 2),  // wall-like off-grid column
                BlockKey.pack(0, 90, 0),  // spawn-height block above the span
                BlockKey.pack(3, 100, 3));

        assertTrue(TierOnePlanner.select(diff, snapshot()).isEmpty());
        assertFalse(TierOnePlanner.select(
                Set.of(BlockKey.pack(3, 84, 3)), snapshot()).isEmpty());
    }

    @Test
    void sampledColumnsCarryTheBaselineSurfaceHeight() {
        List<TierOnePlanner.Column> columns = TierOnePlanner.sampledColumns(snapshot());

        assertEquals(9, columns.size(), "3x3 sampled grid for a 9x9 arena");
        assertTrue(columns.contains(new TierOnePlanner.Column(0, 0, SURFACE_Y)));
        assertTrue(columns.contains(new TierOnePlanner.Column(6, 6, SURFACE_Y)));
        assertFalse(columns.contains(new TierOnePlanner.Column(1, 1, SURFACE_Y)));
    }
}
