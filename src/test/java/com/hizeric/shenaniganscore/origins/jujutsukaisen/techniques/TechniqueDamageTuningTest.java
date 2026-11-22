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
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueDamageTuningTest {

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
    void everyProjectileReadsItsDamageOverride() throws IOException {
        load("""
                abilities:
                  techniques:
                    blue:
                      damage: 2.5
                    red:
                      damage: 19.0
                    fuga:
                      damage: 25.0
                    piercing-blood:
                      damage: 15.0
                    purple:
                      damage: 25.0
                """);
        assertEquals(2.5, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.BlueProjectile.configuredDamage(), 0.0001);
        assertEquals(19.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Projectile.configuredDamage(), 0.0001);
        assertEquals(25.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Projectile.configuredDamage(), 0.0001);
        assertEquals(15.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.configuredDamage(), 0.0001);
        assertEquals(25.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.PurpleProjectile.configuredDamage(), 0.0001);
    }

    @Test
    void configuredDamageWithinHolderRangeIsNotReClampedByProjectiles() {
        // The holder accepts 0..200. Each projectile must use the configured value verbatim
        // instead of silently reducing it to a legacy, lower local cap.
        assertEquals(120.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.BlueProjectile.effectiveDamage(120.0), 0.0001);
        assertEquals(120.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Projectile.effectiveDamage(120.0), 0.0001);
        assertEquals(120.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Projectile.effectiveDamage(120.0), 0.0001);
        assertEquals(120.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.effectiveDamage(120.0), 0.0001);
        assertEquals(120.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.PurpleProjectile.effectiveDamage(120.0), 0.0001);
    }

    @Test
    void shippedDefaultsAreTheApprovedBuffs() throws IOException {
        load("config-version: 1\n");
        assertEquals(2.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue.BlueProjectile.configuredDamage(), 0.0001);
        assertEquals(18.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Projectile.configuredDamage(), 0.0001);
        assertEquals(24.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Projectile.configuredDamage(), 0.0001);
        assertEquals(14.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.configuredDamage(), 0.0001);
        assertEquals(24.0, com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.PurpleProjectile.configuredDamage(), 0.0001);
        // The four buffed techniques must be strictly above their old hardcoded literals.
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red.Projectile.DEFAULT_DAMAGE_PER_POWER > 10.0);
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga.Projectile.DEFAULT_DAMAGE_PER_POWER > 14.0);
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood.PiercingBloodShot.DEFAULT_DAMAGE_PER_POWER > 7.0);
        assertTrue(com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple.PurpleProjectile.DEFAULT_DAMAGE_PER_POWER > 14.0);
    }
}
