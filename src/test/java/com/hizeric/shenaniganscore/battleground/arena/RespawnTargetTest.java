package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.Location;
import org.bukkit.util.BlockVector;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class RespawnTargetTest {

    private static final Location CONFIGURED = new Location(null, 10, 64, 20);
    private static final Location WORLD_SPAWN = new Location(null, 0, 64, 0);

    private static ArenaRegion region() {
        return new ArenaRegion(UUID.randomUUID(), "world",
                new BlockVector(0, 0, 0), new BlockVector(10, 100, 10), 1L, 1L, "CLIPBOARD");
    }

    @Test
    void configuredReturnPointWins() {
        assertSame(CONFIGURED, RespawnTarget.resolve(CONFIGURED, WORLD_SPAWN));
    }

    @Test
    void worldSpawnIsUsedWhenNoReturnPointIsConfigured() {
        assertSame(WORLD_SPAWN, RespawnTarget.resolve(null, WORLD_SPAWN));
    }

    @Test
    void aConfiguredPointInsideTheArenaFallsBackToTheWorldSpawn() {
        Location inside = new Location(null, 5, 64, 5);
        Location outside = new Location(null, 50, 64, 50);
        assertSame(outside, RespawnTarget.resolve(inside, outside, region()));
    }

    @Test
    void whenBothCandidatesAreInsideTheArenaAnOutsidePointIsProduced() {
        ArenaRegion region = region();
        Location configured = new Location(null, 5, 64, 5);
        Location worldSpawn = new Location(null, 6, 64, 6);
        Location chosen = RespawnTarget.resolve(configured, worldSpawn, region);

        assertEquals(region.min().getBlockX() - 1, chosen.getBlockX());
        assertEquals(region.min().getBlockZ() - 1, chosen.getBlockZ());
        assertFalse(chosen.getBlockX() >= region.min().getBlockX()
                && chosen.getBlockX() <= region.max().getBlockX()
                && chosen.getBlockZ() >= region.min().getBlockZ()
                && chosen.getBlockZ() <= region.max().getBlockZ());
    }
}
