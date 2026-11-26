package com.hizeric.shenaniganscore.core.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbilityRegistryTest {

    @Test
    void sharedIconMaterialDoesNotBlockRegistration() {
        List<String> warnings = new ArrayList<>();
        AbilityRegistry registry = new AbilityRegistry(warnings::add);
        AbilityDefinition first = definition("test:first", Material.STONE);
        AbilityDefinition second = definition("test:second", Material.STONE);

        registry.register(first);
        registry.register(second);

        assertEquals(List.of(first, second), new ArrayList<>(registry.all()),
                "both abilities must be registered in insertion order even with the same icon");
        assertTrue(warnings.isEmpty(), "a shared icon must not produce a warning: " + warnings);
    }

    @Test
    void duplicateIdIsStillRejectedWithWarning() {
        List<String> warnings = new ArrayList<>();
        AbilityRegistry registry = new AbilityRegistry(warnings::add);
        AbilityDefinition first = definition("test:same", Material.STONE);
        AbilityDefinition duplicate = definition("test:same", Material.DIRT);

        registry.register(first);
        registry.register(duplicate);

        assertEquals(1, registry.size());
        assertSame(first, registry.get(AbilityId.of("test:same")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0).contains("Duplicate ability id"), warnings.toString());
    }

    private static AbilityDefinition definition(String rawId, Material icon) {
        return new AbilityDefinition(
                AbilityId.of(rawId),
                Component.text(rawId),
                icon,
                10,
                () -> 5,
                1.0,
                (player, power) -> { },
                player -> false,
                false);
    }
}
