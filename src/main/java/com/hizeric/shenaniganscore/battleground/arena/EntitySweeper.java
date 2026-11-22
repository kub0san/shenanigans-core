package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.NamespacedKey;

/** Removes loose entities from the arena before a restore. Players and tagged furniture survive. */
public final class EntitySweeper {

    private static final NamespacedKey FURNITURE =
            NamespacedKey.fromString("shenaniganscore:arena-furniture");

    private EntitySweeper() { }

    public static int sweep(ArenaRegion region) {
        if (region == null) return 0;
        World world = region.world();
        if (world == null) return 0;

        int removed = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof Player) continue;
            if (!region.contains(entity.getLocation())) continue;
            if (isFurniture(entity)) continue;
            entity.remove();
            removed++;
        }
        return removed;
    }

    /** Marks an entity as part of the arena set dressing so the sweeper leaves it alone. */
    public static void markFurniture(Entity entity) {
        if (entity instanceof PersistentDataHolder holder) {
            holder.getPersistentDataContainer().set(FURNITURE, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        }
    }

    private static boolean isFurniture(Entity entity) {
        if (!(entity instanceof PersistentDataHolder holder)) return false;
        Byte flag = holder.getPersistentDataContainer().get(FURNITURE, org.bukkit.persistence.PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }
}
