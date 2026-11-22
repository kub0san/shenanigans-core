package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The market, rail bindings and /ability command all read this one registry. Purple
 * must actually be in it — a duplicate id or icon is silently dropped with only a warning.
 */
class BattlegroundCatalogTest {

    private static AbilityRegistry catalogue(List<String> warnings) {
        AbilityRegistry registry = new AbilityRegistry(warnings::add);
        JujutsuAbilities.register(registry);
        return registry;
    }

    @Test
    void purpleIsInTheCatalogueTheShopReads() {
        List<String> warnings = new ArrayList<>();
        AbilityRegistry registry = catalogue(warnings);

        assertEquals(List.of(), warnings, "every ability must register cleanly");
        assertEquals(10, registry.size());
        AbilityDefinition purple = registry.get(AbilityId.of("jjk:purple"));
        assertNotNull(purple);
        assertTrue(purple.price() > 0);
        assertTrue(purple.power() > 0);
        assertNotNull(purple.displayName());
    }

    @Test
    void everyAbilityHasADistinctShopIcon() {
        AbilityRegistry registry = catalogue(new ArrayList<>());
        HashSet<Object> icons = new HashSet<>();
        for (AbilityDefinition definition : registry.all()) {
            icons.add(definition.iconMaterial());
        }

        assertEquals(registry.size(), icons.size());
    }
}
