package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashSwordTuningTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        AbilityTuningConfig.load(null, null);
    }

    private void load(String yaml) throws IOException {
        File target = new File(folder.toFile(), "config.yml");
        Files.writeString(target.toPath(), yaml, StandardCharsets.UTF_8);
        AbilityTuningConfig.load(target, Logger.getLogger("test"));
    }

    @Test
    void cooldownComesFromYamlAndAppearsInLore() throws IOException {
        load("""
                abilities:
                  items:
                    dash-sword:
                      cooldown: 55
                """);
        assertEquals(55, DashSwordItem.cooldownSeconds());
        String lore = String.join("\n", DashSwordItem.lore());
        assertTrue(lore.contains("55s cooldown"), "expected tuned cooldown in lore: " + lore);
        assertFalse(lore.contains("6s cooldown"), "stale 6s lore must be gone: " + lore);
    }

    @Test
    void missingEntryUsesTheShippedDefault() throws IOException {
        load("config-version: 1\n");
        assertEquals(30, DashSwordItem.cooldownSeconds());
        assertTrue(String.join("\n", DashSwordItem.lore()).contains("30s cooldown"));
    }
}
