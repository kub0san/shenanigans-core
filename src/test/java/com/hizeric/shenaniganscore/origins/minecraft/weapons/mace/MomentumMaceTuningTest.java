package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

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

class MomentumMaceTuningTest {
    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() { AbilityTuningConfig.load(null, null); }

    private void load(String yaml) throws IOException {
        File target = new File(folder.toFile(), "config.yml");
        Files.writeString(target.toPath(), yaml, StandardCharsets.UTF_8);
        AbilityTuningConfig.load(target, Logger.getLogger("test"));
    }

    @Test
    void tunedValuesReachAccessorsAndLore() throws IOException {
        load("""
                abilities:
                  items:
                    momentum-mace:
                      cooldown: 55
                      max-hit-charges: 5
                      hit-charge-recharge: 4
                      empowered-hit-damage-cap: 15.0
                      depleted-hit-damage-cap: 3.0
                """);
        assertEquals(55, MomentumMaceItem.cooldownSeconds());
        assertEquals(55, MomentumMaceAbility.definition().cooldownSeconds());
        assertEquals(5, MomentumMaceChargeManager.maxCharges());
        assertEquals(4000L, MomentumMaceChargeManager.rechargeMs());
        AbilityTuningConfig cfg = AbilityTuningConfig.current();
        assertEquals(15.0, cfg.boundedDouble(AbilityTuningConfig.Section.ITEMS,
                "momentum-mace", "empowered-hit-damage-cap", 12.0, 0.0, 40.0));
        assertEquals(3.0, cfg.boundedDouble(AbilityTuningConfig.Section.ITEMS,
                "momentum-mace", "depleted-hit-damage-cap", 4.0, 0.0, 40.0));
        String lore = String.join("\n", MomentumMaceItem.lore());
        assertTrue(lore.contains("5 maximum"), lore);
        assertTrue(lore.contains("4s"), lore);
        assertTrue(lore.contains("55s cooldown"), lore);
    }

    @Test
    void missingEntryUsesShippedDefaults() {
        assertEquals(30, MomentumMaceItem.cooldownSeconds());
        assertEquals(3, MomentumMaceChargeManager.maxCharges());
        assertEquals(10000L, MomentumMaceChargeManager.rechargeMs());
    }
}