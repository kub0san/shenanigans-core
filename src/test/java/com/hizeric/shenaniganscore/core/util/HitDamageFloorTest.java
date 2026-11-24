package com.hizeric.shenaniganscore.core.util;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #37 (SP-C-009): the tuning holder accepts a valid configured damage of 0, but technique
 * projectiles replaced it with a hardcoded minimum. The minimum must apply only to positive
 * configurations.
 */
class HitDamageFloorTest {

    @Test
    void configuredZeroStaysZero() {
        assertEquals(0.0, MathUtil.hitDamage(18.0, 0.0, 1.0));
        assertEquals(0.0, MathUtil.hitDamage(0.0, 0.0, 2.0));
    }

    @Test
    void positiveConfigurationKeepsTheShippedMinimum() {
        assertEquals(1.0, MathUtil.hitDamage(0.2, 18.0, 1.0));
        assertEquals(2.0, MathUtil.hitDamage(0.2, 10.0, 2.0));
    }

    @Test
    void positiveConfigurationKeepsALargerComputedHit() {
        assertEquals(27.0, MathUtil.hitDamage(27.0, 18.0, 1.0));
    }

    @Test
    void everyProjectileApplicationUsesTheSharedHelper() throws IOException {
        String[] files = {
                "origins/jujutsukaisen/techniques/red/Projectile.java",
                "origins/jujutsukaisen/techniques/fuga/Projectile.java",
                "origins/jujutsukaisen/techniques/blue/BlueProjectile.java",
                "origins/jujutsukaisen/techniques/piercingblood/PiercingBloodShot.java",
                "origins/jujutsukaisen/techniques/purple/PurpleProjectile.java"
        };
        for (String relative : files) {
            Path path = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore")
                    .resolve(relative);
            assertTrue(Files.isRegularFile(path), "expected source at " + path.toAbsolutePath());
            String source = Files.readString(path, StandardCharsets.UTF_8);
            assertTrue(source.contains("MathUtil.hitDamage("),
                    relative + " must apply the configured damage contract via MathUtil.hitDamage");
        }
    }
}
