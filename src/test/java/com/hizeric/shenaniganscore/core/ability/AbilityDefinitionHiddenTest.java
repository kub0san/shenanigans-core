package com.hizeric.shenaniganscore.core.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityDefinitionHiddenTest {

    @Test
    void hiddenIsCarriedVerbatim() {
        assertTrue(definition(true).hidden());
        assertFalse(definition(false).hidden());
    }

    private static AbilityDefinition definition(boolean hidden) {
        return new AbilityDefinition(
                AbilityId.of("test:subject"),
                Component.text("Subject"),
                Material.STONE,
                10,
                () -> 5,
                1.0,
                (player, power) -> { },
                player -> false,
                hidden);
    }
}
