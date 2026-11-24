package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #39: the loadout editor and the hub inventory snapshot must be two different ownership
 * domains. Editing a kit must not overwrite the snapshot that round end restores.
 */
class LoadoutEditorSessionTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void editorOriginalIsSeparateFromTheHubSnapshotAndRestorable() {
        MockBukkit.mock();
        ArenaInventories inventories = new ArenaInventories();
        UUID id = UUID.randomUUID();
        ItemStack[] hub = {new ItemStack(Material.DIAMOND)};
        ItemStack[] board = {new ItemStack(Material.STONE)};

        inventories.captureEditor(id, new ArenaInventories.Snapshot(hub, null));

        assertFalse(inventories.has(id), "the editor board must not become the hub snapshot");
        ArenaInventories.Snapshot original = inventories.takeEditor(id);
        assertNotNull(original);
        assertEquals(Material.DIAMOND, original.contents()[0].getType());
        assertEquals(Material.STONE, board[0].getType(), "the board was a separate in-memory edit");
        assertNull(inventories.takeEditor(id), "the editor session is consumed exactly once");
    }

    @Test
    void loadoutScreenRestoresTheEditorsOriginalOnClose() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground", "menu", "shell", "InlineLoadoutScreen.java");
        assertTrue(Files.isRegularFile(path), "expected source at " + path.toAbsolutePath());
        String source = Files.readString(path, StandardCharsets.UTF_8);
        assertTrue(source.contains("protected void onClose("), "the editor needs a close hook");
        assertTrue(source.contains("takeEditor("), "the close hook must restore the captured inventory");
        assertFalse(source.contains("inventories().captureOnce("),
                "the editor must not write into the hub snapshot map");
    }
}
