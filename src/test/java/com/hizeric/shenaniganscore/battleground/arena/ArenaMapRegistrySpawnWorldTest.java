package com.hizeric.shenaniganscore.battleground.arena;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaMapRegistrySpawnWorldTest {
    @Test
    void aSavedReturnPointKeepsItsOwnWorld() throws Exception {
        CommentedConfigurationNode spawn = CommentedConfigurationNode.root();
        spawn.node("world").set("main");
        spawn.node("x").set(12.5);

        assertEquals("main", ArenaMapRegistry.spawnWorldName(spawn, "arena"),
                "the persisted spawn.world must win over the arena world");
    }

    @Test
    void legacySpawnWithoutAWorldFallsBackToTheArenaWorld() throws Exception {
        CommentedConfigurationNode spawn = CommentedConfigurationNode.root();
        spawn.node("x").set(12.5);

        assertEquals("arena", ArenaMapRegistry.spawnWorldName(spawn, "arena"),
                "older arena.yml files have no spawn.world and must keep the old behaviour");
    }
}
