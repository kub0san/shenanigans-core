package com.hizeric.shenaniganscore.battleground.menu.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.bukkit.Material;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The interface palette is config-driven with red defaults; bad input never crashes a reload. */
class GuiStyleTest {

    @AfterEach
    void restoreDefaults() {
        GuiStyle.reset();
    }

    @Test
    void defaultsAreRed() {
        GuiStyle.reset();
        assertEquals(Material.RED_STAINED_GLASS_PANE, GuiStyle.glass());
        assertEquals("<#e06c75>", GuiStyle.accent());
    }

    @Test
    void overridesBothPaletteEntries() {
        GuiStyle.apply("PINK_STAINED_GLASS_PANE", "<color:#FF69B4>");
        assertEquals(Material.PINK_STAINED_GLASS_PANE, GuiStyle.glass());
        assertEquals("<color:#FF69B4>", GuiStyle.accent());
    }

    @Test
    void badMaterialFallsBackToRed() {
        GuiStyle.apply("NOT_A_MATERIAL", "<#ff5555>");
        assertEquals(Material.RED_STAINED_GLASS_PANE, GuiStyle.glass());
        assertEquals("<#ff5555>", GuiStyle.accent());
    }

    @Test
    void malformedAccentFallsBackToRed() {
        GuiStyle.apply("LIME_STAINED_GLASS_PANE", "pink");
        assertEquals(Material.LIME_STAINED_GLASS_PANE, GuiStyle.glass());
        assertEquals("<#e06c75>", GuiStyle.accent());
    }

    @Test
    void missingKeysKeepDefaults(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("config.yml"), "config-version: 1\n");
        GuiStyle.apply(folder.toFile());
        assertEquals(Material.RED_STAINED_GLASS_PANE, GuiStyle.glass());
        assertEquals("<#e06c75>", GuiStyle.accent());
    }

    @Test
    void unparseableFileFallsBackToDefaults(@TempDir Path folder) throws IOException {
        Files.writeString(folder.resolve("config.yml"), "interface: [broken");
        GuiStyle.apply(folder.toFile());
        assertEquals(Material.RED_STAINED_GLASS_PANE, GuiStyle.glass());
        assertEquals("<#e06c75>", GuiStyle.accent());
    }
}
