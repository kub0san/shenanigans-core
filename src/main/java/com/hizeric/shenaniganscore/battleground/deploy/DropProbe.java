package com.hizeric.shenaniganscore.battleground.deploy;

/**
 * Pure, world-free view of the terrain a drop-zone search reads. Kept free of Bukkit types so the
 * geometry decisions in {@link DropZone} can be unit-tested; {@code BukkitDropProbe} adapts it onto
 * a live {@code World}.
 */
public interface DropProbe {

    /** True when the block at this position is neither solid nor liquid (open air). */
    boolean isAirLike(int x, int y, int z);

    /** True when the block at this position is a liquid (water or lava). */
    boolean isLiquid(int x, int y, int z);

    /**
     * O(1) surface hint — the first non-air block from above (top of canopy, ceiling, ground),
     * or {@link Integer#MIN_VALUE} when the probe has no heightmap. A live world uses it to avoid
     * scanning every Y value for every sampled column and to cap the release height so a diver is
     * never released inside a tree or against a roof. Pure test probes leave the default in place.
     */
    default int surfaceY(int x, int z) {
        return Integer.MIN_VALUE;
    }

    /** Solid, standable block — not air-like, not liquid. Used for structural stability probes. */
    default boolean isSolid(int x, int y, int z) {
        return !isAirLike(x, y, z) && !isLiquid(x, y, z);
    }
}