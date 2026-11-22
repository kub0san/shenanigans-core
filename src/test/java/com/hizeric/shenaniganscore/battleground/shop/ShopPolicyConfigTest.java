package com.hizeric.shenaniganscore.battleground.shop;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopPolicyConfigTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        ShopPolicyConfig.load(null, null);
    }

    private ShopPolicyConfig load(String name, String yaml) throws IOException {
        File target = new File(folder.toFile(), name);
        Files.writeString(target.toPath(), yaml, StandardCharsets.UTF_8);
        return ShopPolicyConfig.load(target, Logger.getLogger("test"));
    }

    @Test
    void defaultsDisableKillstreakRequirements() {
        assertFalse(ShopPolicyConfig.defaults().killstreakRequirements());
    }

    @Test
    void aTrueValueIsLoaded() throws IOException {
        ShopPolicyConfig config = load("config.yml", """
                shop:
                  killstreak-requirements: true
                """);
        assertTrue(config.killstreakRequirements());
    }

    @Test
    void aNonBooleanValueFallsBackToFalse() throws IOException {
        ShopPolicyConfig config = load("config.yml", """
                shop:
                  killstreak-requirements: yes-please
                """);
        assertFalse(config.killstreakRequirements());
    }

    @Test
    void loadingASecondFileReplacesCurrent() throws IOException {
        load("config.yml", "shop:\n  killstreak-requirements: true\n");
        assertTrue(ShopPolicyConfig.current().killstreakRequirements());
        load("config.yml", "shop:\n  killstreak-requirements: false\n");
        assertFalse(ShopPolicyConfig.current().killstreakRequirements());
    }

    @Test
    void anUnreadableFileInstallsDefaultsAndKeepsCurrentNonNull() {
        ShopPolicyConfig config = ShopPolicyConfig.load(
                new File(folder.toFile(), "missing.yml"), Logger.getLogger("test"));
        assertFalse(config.killstreakRequirements());
        assertFalse(ShopPolicyConfig.current().killstreakRequirements());
    }
}
