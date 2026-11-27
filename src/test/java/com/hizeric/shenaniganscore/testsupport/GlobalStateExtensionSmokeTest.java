package com.hizeric.shenaniganscore.testsupport;

import com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves the extension actually isolates globals between tests. */
@ExtendWith(GlobalStateExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GlobalStateExtensionSmokeTest {

    @TempDir
    Path folder;

    @Test
    @Order(1)
    void aTestCanInstallNonDefaultGlobals() throws IOException {
        Path config = folder.resolve("config.yml");
        Files.writeString(config, """
                abilities:
                  items:
                    dash-sword:
                      cooldown: 45
                """, StandardCharsets.UTF_8);
        AbilityTuningConfig.load(config.toFile(), null);
        ShopPolicyConfig.apply(true);

        assertEquals(45, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));
        assertTrue(ShopPolicyConfig.current().killstreakRequirements());
    }

    @Test
    @Order(2)
    void theNextTestSeesTheShippedBaseline() {
        assertEquals(30, AbilityTuningConfig.current().cooldown(ITEMS, "dash-sword", 30));
        assertFalse(ShopPolicyConfig.current().killstreakRequirements());
    }
}
