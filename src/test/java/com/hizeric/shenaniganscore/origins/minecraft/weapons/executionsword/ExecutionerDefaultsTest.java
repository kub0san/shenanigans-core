package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExecutionerDefaultsTest {

    @Test
    void cooldownDefaultIsNinety() {
        assertEquals(90, ExecutionerSwordItem.DEFAULT_COOLDOWN_SECONDS);
    }

    @Test
    void executeThresholdIsSixHp() {
        assertEquals(6.0, ExecutionerSwordItem.EXECUTE_THRESHOLD, 1e-9);
    }

    @Test
    void armDurationIsSixSeconds() {
        assertEquals(6000L, ExecutionerSwordItem.ARM_DURATION_MS);
    }

    @Test
    void priceIsFiveHundred() {
        assertEquals(500, ExecutionerSwordItem.PRICE);
    }

    @Test
    void abilityIdIsNamespaced() {
        assertEquals("minecraft:executioner_sword", ExecutionerSwordItem.ABILITY_ID.toString());
    }

    @Test
    void cooldownReadsShippedDefaultWhenNoConfig() {
        assertEquals(90, AbilityTuningConfig.defaults().cooldown(ITEMS, "executioner-sword", 90));
    }
}