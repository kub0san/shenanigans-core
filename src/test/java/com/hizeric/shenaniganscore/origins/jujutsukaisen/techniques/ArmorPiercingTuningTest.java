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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArmorPiercingTuningTest {

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
    void allThreeCanDisableArmorPiercing() throws IOException {
        load("""
                abilities:
                  techniques:
                    piercing-blood:
                      armor-piercing: false
                    purple:
                      armor-piercing: false
                    world-slash:
                      armor-piercing: false
                """);
        assertFalse(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.armorPiercingEnabled());
        assertFalse(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.PurpleProjectile.armorPiercingEnabled());
        assertFalse(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.WorldSlashCast.armorPiercingEnabled());
    }

    @Test
    void allThreeDefaultToArmorPiercingOn() throws IOException {
        load("config-version: 1\n");
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.armorPiercingEnabled());
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.PurpleProjectile.armorPiercingEnabled());
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.WorldSlashCast.armorPiercingEnabled());
    }

    @Test
    void worldSlashDamageStaysHardcoded() {
        assertThrows(NoSuchMethodException.class, () ->
                com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash.WorldSlashCast.class
                        .getMethod("configuredDamage"));
    }
}
