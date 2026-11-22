package com.hizeric.shenaniganscore.battleground.arena;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/** D5: after the plugin disables WorldEdit, late calls must be inert instead of hitting the relighter. */
class WorldEditBridgeDisableTest {

    @AfterEach
    void reEnable() {
        WorldEditBridge.setEnabled(true);
    }

    @Test
    void loadIsInertAfterDisable(@TempDir Path temp) throws IOException {
        Path junk = temp.resolve("arena.schem");
        Files.writeString(junk, "not a schematic");

        WorldEditBridge.setEnabled(false);

        assertDoesNotThrow(() -> WorldEditBridge.load(junk.toFile()));
        assertNull(WorldEditBridge.load(junk.toFile()));
    }
}
