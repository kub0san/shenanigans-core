package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

/**
 * Removes loose entities from the arena before a restore. Players and tagged furniture survive.
 *
 * <p>Uses a bounding-box query, so only entities in chunks the arena overlaps are visited. The
 * first version walked every entity in the whole world and ran a world lookup per entity. Call from
 * the main thread. Entities in unloaded chunks are not touched; they load back as they were.</p>
 */
public final class EntitySweeper {

    private static final NamespacedKey FURNITURE =
            NamespacedKey.fromString("shenaniganscore:arena-furniture");

    private EntitySweeper() { }

    public static int sweep(ArenaRegion region) {
        if (region == null) return 0;
        World world = region.world();
        if (world == null) return 0;

        BoundingBox box = new BoundingBox(
                region.min().getBlockX(), region.min().getBlockY(), region.min().getBlockZ(),
                region.max().getBlockX() + 1.0, region.max().getBlockY() + 1.0,
                region.max().getBlockZ() + 1.0);

        int removed = 0;
        // The query returns a snapshot collection, so removing while iterating is safe.
        for (Entity entity : world.getNearbyEntities(box)) {
            if (entity instanceof Player) continue;
            if (isFurniture(entity)) continue;
            Location at = entity.getLocation();
            if (!region.containsBlock(at.getBlockX(), at.getBlockY(), at.getBlockZ())) continue;
            entity.remove();
            removed++;
        }
        return removed;
    }

    /** Marks an entity as part of the arena set dressing so the sweeper leaves it alone. */
    public static void markFurniture(Entity entity) {
        if (entity instanceof PersistentDataHolder holder) {
            holder.getPersistentDataContainer().set(FURNITURE, PersistentDataType.BYTE, (byte) 1);
        }
    }

    private static boolean isFurniture(Entity entity) {
        if (!(entity instanceof PersistentDataHolder holder)) return false;
        Byte flag = holder.getPersistentDataContainer().get(FURNITURE, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
