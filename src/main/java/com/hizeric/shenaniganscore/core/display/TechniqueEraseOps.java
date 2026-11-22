package com.hizeric.shenaniganscore.core.display;

import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared client-side erase plumbing for technique visuals.
 *
 * <p>Both the purple and world-slash builders used to carry byte-identical copies of
 * {@link #sendErase(World, Location, List, Set)} and {@link #restore(List, Set)}. The blocks are
 * never removed from the world: air is sent per-player through {@link ClientBlockUtils}, and the
 * affected players are recorded so the hide can be reverted when the technique ends.</p>
 */
public final class TechniqueEraseOps {

    /** Players farther than this from the erase centre never receive the fake blocks. */
    public static final double ERASE_RANGE_SQ = 128.0 * 128.0;

    private static final BlockData AIR = Material.AIR.createBlockData();

    private TechniqueEraseOps() {}

    /**
     * Hide {@code newly} positions as client-side air for every player within
     * {@link #ERASE_RANGE_SQ} of {@code centre}, recording those players in {@code viewers} so the
     * hide can be reverted later.
     */
    public static void sendErase(World w, Location centre, List<Location> newly, Set<UUID> viewers) {
        if (newly.isEmpty()) return;
        Map<Location, BlockData> map = new HashMap<>(newly.size());
        for (Location l : newly) map.put(l, AIR);
        for (Player pl : w.getPlayers()) {
            if (pl.getLocation().distanceSquared(centre) <= ERASE_RANGE_SQ) {
                ClientBlockUtils.sendFakeBlocks(pl, map);
                viewers.add(pl.getUniqueId());
            }
        }
    }

    /** Revert a previously sent erase for every still-online recorded viewer. */
    public static void restore(List<Location> locs, Set<UUID> viewers) {
        for (UUID id : viewers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) ClientBlockUtils.revertBlocks(pl, locs);
        }
    }
}
