package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques;

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

class TechniqueCooldownTuningTest {

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
    void everyTechniqueReadsItsOverrideWithNoStaleCapture() throws IOException {
        load("""
                abilities:
                  techniques:
                    blue:
                      cooldown: 31
                    red:
                      cooldown: 41
                    fuga:
                      cooldown: 76
                    piercing-blood:
                      cooldown: 46
                    purple:
                      cooldown: 61
                    world-slash:
                      cooldown: 91
                """);
        assertEquals(31, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.Ability.getCooldownSeconds());
        assertEquals(41, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Ability.getCooldownSeconds());
        assertEquals(76, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Ability.getCooldownSeconds());
        assertEquals(46, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.Ability.getCooldownSeconds());
        assertEquals(61, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.Ability.getCooldownSeconds());
        assertEquals(91, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.Ability.getCooldownSeconds());
    }

    @Test
    void anEmptyConfigUsesTheShippedDefaults() throws IOException {
        load("config-version: 1\n");
        assertEquals(30, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.Ability.getCooldownSeconds());
        assertEquals(40, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Ability.getCooldownSeconds());
        assertEquals(45, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.Ability.getCooldownSeconds());
        assertEquals(60, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.Ability.getCooldownSeconds());
        assertEquals(75, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Ability.getCooldownSeconds());
        assertEquals(90, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.Ability.getCooldownSeconds());
    }
}
