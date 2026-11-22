package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ArenaRegionSpawnTest {

    private static ArenaRegion region() {
        return new ArenaRegion(UUID.randomUUID(), "world",
                new BlockVector(0, 0, 0), new BlockVector(10, 10, 10), 1L, 1L, "CLIPBOARD");
    }

    @Test
    void aFreshRegionHasNoSpawn() {
        assertNull(region().spawn(), "an unset spawn must stay null so callers can fall back");
    }

    @Test
    void spawnIsReplacedImmutably() {
        ArenaRegion original = region();
        ArenaRegion moved = original.spawn(null);
        assertSame(original, moved);
        assertNull(moved.spawn());
    }
}
