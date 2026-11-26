package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.battleground.combat.CommandPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exact {@code /spawn} matching: the battleground-only alias must not swallow other commands. */
class ExactSpawnTest {

    @Test
    void acceptsOnlyTheBareSpawnRoot() {
        for (String command : List.of("/spawn", "/SPAWN", "/minecraft:spawn", "/spawn ", " spawn", "spawn")) {
            assertTrue(CommandPolicy.isExactSpawn(command), "should match: " + command);
        }
    }

    @Test
    void rejectsArgumentsOtherRootsAndNull() {
        for (String command : List.of("/spawn world", "/spawnfoo", "/bg spawn", " /spawn top",
                "/spawn  world", "")) {
            assertFalse(CommandPolicy.isExactSpawn(command), "should not match: " + command);
        }
        assertFalse(CommandPolicy.isExactSpawn(null));
    }
}
