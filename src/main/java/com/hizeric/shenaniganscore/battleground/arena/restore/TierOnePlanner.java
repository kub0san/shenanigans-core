package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot;
import com.hizeric.shenaniganscore.core.util.BlockKey;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure tier-1 essential selection.
 *
 * <p>The real {@code DropZone}/{@link BattlegroundDefaults} rules define which columns matter:
 * a stride-3 grid, the baseline surface block of each sampled column, and a clearance envelope
 * of {@code max(10, 20 + 3) = 23} blocks above it. This mode has no walls and no spawn platform,
 * so off-grid columns and anything above the envelope are never tier-1 essentials.</p>
 */
public final class TierOnePlanner {

    public static final int SAMPLE_STEP = BattlegroundDefaults.DEPLOY_SAMPLE_STEP;
    public static final int CLEARANCE_SPAN = Math.max(
            BattlegroundDefaults.DEPLOY_CLEARANCE_HEIGHT,
            BattlegroundDefaults.DEPLOY_DROP_HEIGHT + BattlegroundDefaults.DEPLOY_SPAWN_CLEARANCE);

    /** A sampled column and the highest solid baseline Y within it. */
    public record Column(int x, int z, int surfaceY) { }

    private TierOnePlanner() { }

    /** Sampled grid anchored at the snapshot minimum, matching {@code DropSiteSelector}. */
    public static List<Column> sampledColumns(ArenaSnapshot snapshot) {
        List<Column> columns = new ArrayList<>();
        if (snapshot == null) return columns;
        for (int x = snapshot.minX(); x <= snapshot.maxX(); x += SAMPLE_STEP) {
            for (int z = snapshot.minZ(); z <= snapshot.maxZ(); z += SAMPLE_STEP) {
                int surface = -1;
                for (int y = snapshot.maxY(); y >= snapshot.minY(); y--) {
                    if (snapshot.isSolidAt(x, y, z)) {
                        surface = y;
                        break;
                    }
                }
                if (surface >= 0) columns.add(new Column(x, z, surface));
            }
        }
        return columns;
    }

    /** Diff positions that are tier-1 essentials: surface block or inside its clearance. */
    public static Set<Long> select(Set<Long> diff, ArenaSnapshot snapshot) {
        Set<Long> selected = new LinkedHashSet<>();
        if (diff == null || diff.isEmpty() || snapshot == null) return selected;

        Map<Long, Column> sampled = new HashMap<>();
        for (Column column : sampledColumns(snapshot)) {
            sampled.put(xzKey(column.x(), column.z()), column);
        }
        for (long packed : diff) {
            int x = BlockKey.unpackX(packed);
            int y = BlockKey.unpackY(packed);
            int z = BlockKey.unpackZ(packed);
            Column column = sampled.get(xzKey(x, z));
            if (column == null) continue;
            int surface = column.surfaceY();
            if (y == surface || (y > surface && y <= surface + CLEARANCE_SPAN)) {
                selected.add(packed);
            }
        }
        return selected;
    }

    private static long xzKey(int x, int z) {
        return ((long) x << 32) ^ (z & 0xFFFF_FFFFL);
    }
}
