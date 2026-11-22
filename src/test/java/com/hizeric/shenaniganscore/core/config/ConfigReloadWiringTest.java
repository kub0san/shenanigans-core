package com.hizeric.shenaniganscore.core.config;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigReloadWiringTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        AbilityTuningConfig.load(null, null);
        ShopPolicyConfig.load(null, null);
    }

    @Test
    void thePluginExposesTheTuningAccessors() throws Exception {
        assertEquals(AbilityTuningConfig.class,
                ShenanigansCore.class.getDeclaredMethod("abilityTuning").getReturnType());
        assertEquals(ShopPolicyConfig.class,
                ShenanigansCore.class.getDeclaredMethod("shopPolicy").getReturnType());
        assertNotNull(ShenanigansCore.class.getDeclaredMethod("reloadTuning"));
    }

    @Test
    void theReloadCommandCallsTheManagerWhichRefreshesTuning() throws IOException {
        Path command = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground", "command", "BattlegroundCommand.java");
        Path manager = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground", "BattlegroundManager.java");
        Path controller = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "battleground", "RoundController.java");
        assertTrue(Files.isRegularFile(command), "expected the command source at " + command.toAbsolutePath());
        assertTrue(Files.isRegularFile(manager), "expected the manager source at " + manager.toAbsolutePath());
        assertTrue(Files.isRegularFile(controller), "expected the controller source at " + controller.toAbsolutePath());
        assertTrue(Files.readString(command, StandardCharsets.UTF_8).contains("reloadConfigAsync("),
                "/bg reload must dispatch through the async manager reload");
        assertTrue(Files.readString(manager, StandardCharsets.UTF_8).contains("reloadConfigAsync("),
                "the facade must delegate the async reload");
        assertTrue(Files.readString(controller, StandardCharsets.UTF_8).contains("reloadTuning()"),
                "the async reload must still refresh the tuning holders");
    }

    @Test
    void aSecondLoadChangesWhatCurrentReturns() throws IOException {
        File target = new File(folder.toFile(), "config.yml");
        Files.writeString(target.toPath(),
                "abilities:\n  items:\n    dash-sword:\n      cooldown: 45\n", StandardCharsets.UTF_8);
        AbilityTuningConfig.load(target, Logger.getLogger("test"));
        assertEquals(45, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));

        Files.writeString(target.toPath(),
                "abilities:\n  items:\n    dash-sword:\n      cooldown: 12\n", StandardCharsets.UTF_8);
        AbilityTuningConfig.load(target, Logger.getLogger("test"));
        assertEquals(12, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));
    }
}
