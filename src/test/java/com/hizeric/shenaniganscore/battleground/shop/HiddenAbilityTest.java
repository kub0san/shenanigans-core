package com.hizeric.shenaniganscore.battleground.shop;

import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordItem;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenAbilityTest {

    @Test
    void domainsAreRegisteredHiddenAndPricedAtZero() {
        AbilityRegistry registry = new AbilityRegistry(new ArrayList<>()::add);
        JujutsuAbilities.register(registry);
        assertEquals(10, registry.size());
        for (AbilityId id : JujutsuAbilities.DOMAINS) {
            AbilityDefinition definition = registry.get(id);
            assertNotNull(definition, "missing domain: " + id);
            assertTrue(definition.hidden(), "domain must be hidden: " + id);
            assertEquals(0, definition.price(), "domain must not carry a price: " + id);
        }
    }

    @Test
    void aSeededItemsAbilityIsNeverSoldAsAStandaloneRow() {
        AbilityRegistry registry = new AbilityRegistry(new ArrayList<>()::add);
        JujutsuAbilities.register(registry);
        List<String> seeded = ShopCatalog.seededAbilities(registry).stream()
                .map(ShopEntry::abilityId)
                .filter(java.util.Objects::nonNull)
                .toList();
        assertTrue(seeded.stream().noneMatch(
                        s -> ShopEntry.abilityIdMatches(s, DashSwordItem.ABILITY_ID.toString())),
                "the Dash Sword ability must be sold only through its item row");
    }

    @Test
    void noDomainReachesTheMarketSeed() {
        AbilityRegistry registry = new AbilityRegistry(new ArrayList<>()::add);
        JujutsuAbilities.register(registry);
        List<String> seeded = ShopCatalog.seededAbilities(registry).stream()
                .map(ShopEntry::abilityId)
                .filter(java.util.Objects::nonNull)
                .toList();
        for (AbilityId id : JujutsuAbilities.DOMAINS) {
            assertTrue(seeded.stream().noneMatch(s -> ShopEntry.abilityIdMatches(s, id.toString())),
                    "hidden domain leaked into the market: " + id);
        }
    }
}
