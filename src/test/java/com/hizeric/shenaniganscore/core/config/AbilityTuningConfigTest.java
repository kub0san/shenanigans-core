package com.hizeric.shenaniganscore.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS;
import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityTuningConfigTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        AbilityTuningConfig.load(null, null);
    }

    private AbilityTuningConfig load(String name, String yaml) throws IOException {
        File target = new File(folder.toFile(), name);
        Files.writeString(target.toPath(), yaml, StandardCharsets.UTF_8);
        return AbilityTuningConfig.load(target, Logger.getLogger("test"));
    }

    @Test
    void defaultsReturnTheCallerSuppliedFallbacks() {
        AbilityTuningConfig config = AbilityTuningConfig.defaults();
        assertEquals(30, config.cooldown(ITEMS, "dash-sword", 30));
        assertEquals(2.0, config.damage("blue", 2.0), 0.0001);
        assertTrue(config.armorPiercing("purple", true));
        assertFalse(config.armorPiercing("purple", false));
    }

    @Test
    void cooldownIsReadFromYamlAndUnderscoreIdsResolveToHyphenKeys() throws IOException {
        AbilityTuningConfig config = load("config.yml", """
                abilities:
                  items:
                    dash-sword:
                      cooldown: 45
                """);
        assertEquals(45, config.cooldown(ITEMS, "dash-sword", 30));
        assertEquals(45, config.cooldown(ITEMS, "dash_sword", 30));
    }

    @Test
    void outOfRangeNumbersFallBackToTheSuppliedDefault() throws IOException {
        AbilityTuningConfig config = load("config.yml", """
                abilities:
                  items:
                    dash-sword:
                      cooldown: 0
                  techniques:
                    red:
                      cooldown: 9999
                      damage: 201.0
                    fuga:
                      damage: -5.0
                """);
        assertEquals(30, config.cooldown(ITEMS, "dash-sword", 30));
        assertEquals(40, config.cooldown(TECHNIQUES, "red", 40));
        assertEquals(18.0, config.damage("red", 18.0), 0.0001);
        assertEquals(24.0, config.damage("fuga", 24.0), 0.0001);
    }

    @Test
    void nonNumericValuesFallBackToTheSuppliedDefault() throws IOException {
        AbilityTuningConfig config = load("config.yml", """
                abilities:
                  items:
                    dash-sword:
                      cooldown: fast
                  techniques:
                    blue:
                      damage: huge
                """);
        assertEquals(30, config.cooldown(ITEMS, "dash-sword", 30));
        assertEquals(2.0, config.damage("blue", 2.0), 0.0001);
    }

    @Test
    void armorPiercingHonorsFalseAndMissingKeysUseTheFallback() throws IOException {
        AbilityTuningConfig config = load("config.yml", """
                abilities:
                  techniques:
                    purple:
                      armor-piercing: false
                """);
        assertFalse(config.armorPiercing("purple", true));
        assertTrue(config.armorPiercing("world_slash", true));
    }

    @Test
    void loadingASecondFileReplacesCurrent() throws IOException {
        load("config.yml", """
                abilities:
                  items:
                    dash-sword:
                      cooldown: 45
                """);
        assertEquals(45, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));
        load("config.yml", """
                abilities:
                  items:
                    dash-sword:
                      cooldown: 12
                """);
        assertEquals(12, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));
    }

    @Test
    void anUnreadableFileInstallsDefaultsAndKeepsCurrentNonNull() throws IOException {
        AbilityTuningConfig config = AbilityTuningConfig.load(
                new File(folder.toFile(), "missing.yml"), Logger.getLogger("test"));
        assertEquals(30, config.cooldown(ITEMS, "dash-sword", 30));
        assertEquals(30, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));
    }
}
