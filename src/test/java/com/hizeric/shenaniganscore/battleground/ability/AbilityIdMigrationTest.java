package com.hizeric.shenaniganscore.battleground.ability;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityIdMigrationTest {

    @Test
    void legacyAbilityIdsBecomeCanonical() {
        assertEquals("minecraft:dash_sword", AbilityIdMigration.apply("battleground:dash_sword"));
        assertEquals("minecraft:dash_sword", AbilityIdMigration.apply("minecraft:dashsword"));
        assertEquals("minecraft:executioners_sword", AbilityIdMigration.apply("executioner_sword"));
        assertEquals("minecraft:executioners_sword", AbilityIdMigration.apply("minecraft:executioner_sword"));
        assertEquals("minecraft:executioners_sword", AbilityIdMigration.apply("minecraft:executionsword"));
        assertEquals("jjk:piercing_blood", AbilityIdMigration.apply("jjk:piercingblood"));
        assertEquals("jjk:world_slash", AbilityIdMigration.apply("jjk:worldslash"));
    }

    @Test
    void currentIdsAndNullAreLeftAlone() {
        assertEquals("jjk:blue", AbilityIdMigration.apply("jjk:blue"));
        assertNull(AbilityIdMigration.apply(null));
    }

    @Test
    void legacyDetectionAndReverseLookup() {
        assertTrue(AbilityIdMigration.isLegacy("battleground:dash_sword"));
        assertFalse(AbilityIdMigration.isLegacy("minecraft:dash_sword"));
        assertEquals("battleground:dash_sword",
                AbilityIdMigration.legacyOf("minecraft:dash_sword"));
        assertEquals("minecraft:executioner_sword",
                AbilityIdMigration.legacyOf("minecraft:executioners_sword"));
        assertNull(AbilityIdMigration.legacyOf("jjk:blue"));
    }

    @Test
    void applyAllCanonicalizesValuesWithoutDroppingKeys() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("rail", "battleground:dash_sword");
        values.put("other", "jjk:blue");

        AbilityIdMigration.applyAll(values);

        assertEquals(2, values.size());
        assertEquals("minecraft:dash_sword", values.get("rail"));
        assertEquals("jjk:blue", values.get("other"));
    }
}
