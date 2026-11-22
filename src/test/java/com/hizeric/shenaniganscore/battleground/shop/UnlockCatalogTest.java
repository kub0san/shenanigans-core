package com.hizeric.shenaniganscore.battleground.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The unlock catalogue seam: categories, validation, and lock progress. */
class UnlockCatalogTest {

    @Test
    void filtersCustomItemsAbilitiesAndUltimates() {
        List<ShopEntry> entries = List.of(
                ShopEntry.customItem("dash", Material.FEATHER, "Dash", 10, 0,
                        Map.of("type", "DIAMOND_SWORD")),
                ShopEntry.ability("blue", Material.LIGHT_BLUE_CONCRETE, "Blue", 40, 5, "jjk:blue"),
                ShopEntry.ultimate("void", Material.END_CRYSTAL, "Void", 100, 10, "jjk:void"));

        assertEquals(1, ShopEntry.filter(entries, ShopEntry.Filter.CUSTOM_ITEMS).size());
        assertEquals(1, ShopEntry.filter(entries, ShopEntry.Filter.ABILITIES).size());
        assertEquals(1, ShopEntry.filter(entries, ShopEntry.Filter.ULTIMATES).size());
        assertEquals(3, ShopEntry.filter(entries, ShopEntry.Filter.ALL).size());
    }

    @Test
    void negativeStreakRequirementIsRejected() {
        List<ShopEntry> entries = List.of(
                ShopEntry.ability("blue", Material.STONE, "Blue", 40, -1, "jjk:blue"));

        assertTrue(ShopEntry.validate(entries).stream()
                .anyMatch(problem -> problem.contains("Negative streak")));
    }

    @Test
    void customItemsHaveNoStreakGate() {
        ShopEntry dash = ShopEntry.customItem("dash", Material.FEATHER, "Dash", 10, 0, Map.of());

        assertEquals(0, dash.streakRequired());
        assertTrue(dash.gate(true, 0).canUse(), "a bought custom item works from deploy");
    }

    @Test
    void abilityIdsMatchAcrossNamespacedAndBareForms() {
        assertTrue(ShopEntry.abilityIdMatches("jjk:blue", "blue"),
                "the cast path uses namespaced ids, the seeded catalog may hold bare ids");
        assertTrue(ShopEntry.abilityIdMatches("blue", "jjk:blue"));
        assertTrue(ShopEntry.abilityIdMatches("jjk:blue", "jjk:blue"));
        assertFalse(ShopEntry.abilityIdMatches("jjk:blue", "red"));
        assertFalse(ShopEntry.abilityIdMatches(null, "blue"));
    }

    @Test
    void unknownCategoryIsRejected() {
        assertNull(ShopEntry.parseCategory("nonsense"));
        assertEquals(ShopEntry.Category.ULTIMATE, ShopEntry.parseCategory("uLtImAtE"));
    }

    @Test
    void ultimateRequiresAnAbilityId() {
        ShopEntry entry = new ShopEntry("void", ShopEntry.Category.ULTIMATE, List.of(),
                Material.END_CRYSTAL, "Void", 100, true, null, Map.of(), 5);

        assertTrue(ShopEntry.validate(List.of(entry)).stream()
                .anyMatch(problem -> problem.contains("Ultimate")));
    }

    @Test
    void gateExposesBothGatesAndProgress() {
        ShopEntry entry = ShopEntry.ability("blue", Material.STONE, "Blue", 40, 5, "jjk:blue");

        ShopEntry.Gate locked = entry.gate(false, 2);
        assertFalse(locked.canUse());
        assertEquals(2, locked.progress());
        assertEquals(5, locked.required());
        assertEquals(40, locked.price());

        ShopEntry.Gate notOwned = entry.gate(false, 9);
        assertFalse(notOwned.canUse(), "a streak alone must not grant use");

        ShopEntry.Gate ready = entry.gate(true, 5);
        assertTrue(ready.canUse());
        assertEquals(5, ready.progress());
    }
}
