package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.logging.Logger;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.ITEMS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExecutionerDefaultsTest {

    @AfterEach
    void restoreDefaults() {
        AbilityTuningConfig.load(null, null);
    }

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
    void cutsceneLengthIsTwentyFourTicks() {
        assertEquals(24, ExecutionerSwordEffect.CUTSCENE_TICKS);
    }

    @Test
    void priceIsFiveHundred() {
        assertEquals(500, ExecutionerSwordItem.PRICE);
    }

    @Test
    void abilityIdIsNamespaced() {
        assertEquals("minecraft:executioners_sword", ExecutionerSwordItem.ABILITY_ID.toString());
    }

    @Test
    void everyLegacyIdStillResolvesToTheCanonicalItem() {
        assertEquals("executioner_sword", ExecutionerSwordId.OLD_ID);
        for (String legacy : ExecutionerSwordId.LEGACY_IDS) {
            assertEquals("minecraft:executioners_sword", ExecutionerSwordId.migrate(legacy));
        }
        assertEquals("minecraft:executioners_sword",
                ExecutionerSwordId.migrate("minecraft:executioners_sword"));
        assertEquals("jjk:blue", ExecutionerSwordId.migrate("jjk:blue"));
    }

    @Test
    void registeredDefinitionIsVisibleWithDiamondSwordIcon() {
        var definition = ExecutionerSwordAbility.definition(new ExecutionerSwordHitListener());
        assertEquals("minecraft:executioners_sword", definition.id().toString());
        assertEquals(Material.DIAMOND_SWORD, definition.iconMaterial());
        assertEquals(false, definition.hidden());
    }

    @Test
    void cooldownReadsShippedValueFromConfig() throws Exception {
        URL url = ExecutionerDefaultsTest.class.getResource("/config.yml");
        assertNotNull(url);
        AbilityTuningConfig.load(new File(url.toURI()), Logger.getLogger("test"));
        // Fallback (7) differs from the shipped value (90) to prove the shipped value is read.
        assertEquals(90, AbilityTuningConfig.current().cooldown(ITEMS, "executioners-sword", 7));
        assertEquals(90, ExecutionerSwordItem.cooldownSeconds());
    }
}
