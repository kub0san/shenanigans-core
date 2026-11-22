package com.hizeric.shenaniganscore.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigAutoUpdaterTest {

    @TempDir Path folder;

    private ConfigAutoUpdater updater() {
        return new ConfigAutoUpdater(folder.toFile(),
                name -> getClass().getResourceAsStream("/" + name), Logger.getLogger("test"));
    }

    @Test
    void runExtractsBothManagedFilesFromTheBundledResources() throws Exception {
        assertEquals(2, updater().run());

        File main = new File(folder.toFile(), "config.yml");
        File battleground = new File(folder.toFile(), "battleground/config.yml");
        assertTrue(main.exists());
        assertTrue(battleground.exists());
        assertTrue(Files.readString(main.toPath()).contains("config-version: 1"));
        assertTrue(Files.readString(battleground.toPath()).contains("config-version: 1"));
    }

    @Test
    void aSecondRunChangesNothing() {
        ConfigAutoUpdater updater = updater();
        assertEquals(2, updater.run());
        assertEquals(0, updater.run(), "a second run must not rewrite either stub");
    }

    @Test
    void obsoleteKeysFromAnOldConfigAreStrippedOnUpdate() throws Exception {
        File main = new File(folder.toFile(), "config.yml");
        Files.createDirectories(main.toPath().getParent());
        Files.writeString(main.toPath(), "config-version: 0\njujutsukaisen:\n  malevolentshrine:\n    radius: 999\n");
        File battlegroundFile = new File(folder.toFile(), "battleground/config.yml");
        Files.createDirectories(battlegroundFile.toPath().getParent());
        Files.writeString(battlegroundFile.toPath(),
                "config-version: 0\neconomy:\n  kill: 999\narena:\n  round:\n    duration-seconds: 777\n");

        assertEquals(2, updater().run());

        String afterMain = Files.readString(main.toPath());
        assertFalse(afterMain.contains("radius: 999"), "the legacy key is gone");
        assertTrue(afterMain.contains("config-version: 1"));
        String afterBg = Files.readString(new File(folder.toFile(), "battleground/config.yml").toPath());
        assertFalse(afterBg.contains("kill: 999"), "an obsolete economy key is stripped");
        assertTrue(afterBg.contains("duration-seconds: 777"), "the live arena.round key survives the merge");
    }

    @Test
    void runtimeDataFilesAreNeverTouched() throws Exception {
        File shim = new File(folder.toFile(), "battleground/shop.yml");
        Files.createDirectories(shim.toPath().getParent());
        String marker = "items:\n  sword:\n    price: 9999\n";
        Files.writeString(shim.toPath(), marker, StandardCharsets.UTF_8);

        updater().run();

        assertEquals(marker, Files.readString(shim.toPath()));
        assertFalse(new File(shim.getPath() + ".backup").exists());
    }
}
