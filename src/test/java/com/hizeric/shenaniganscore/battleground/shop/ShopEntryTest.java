package com.hizeric.shenaniganscore.battleground.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShopEntryTest {

    @Test
    void filterSeparatesAbilitiesItemsAndJujutsu() {
        List<ShopEntry> entries = List.of(
                ShopEntry.ability("blue", Material.LIGHT_BLUE_CONCRETE, "Blue", 40, "jjk:blue", true),
                ShopEntry.ability("dash", Material.FEATHER, "Dash", 10, "test:dash", false),
                ShopEntry.item("sword", Material.DIAMOND_SWORD, "Sword", 25, Map.of("type", "DIAMOND_SWORD")));

        assertEquals(2, ShopEntry.filter(entries, ShopEntry.Filter.ABILITIES).size());
        assertEquals(1, ShopEntry.filter(entries, ShopEntry.Filter.CUSTOM_ITEMS).size());
        assertEquals(0, ShopEntry.filter(entries, ShopEntry.Filter.ULTIMATES).size());
        assertEquals(3, ShopEntry.filter(entries, ShopEntry.Filter.ALL).size());
    }

    @Test
    void disabledEntriesAreHiddenFromEveryFilter() {
        ShopEntry disabled = new ShopEntry("old", ShopEntry.Category.ITEM, List.of(), Material.BARRIER,
                "Old", 1, false, null, Map.of(), 0);
        assertTrue(ShopEntry.filter(List.of(disabled), ShopEntry.Filter.ALL).isEmpty());
    }

    @Test
    void validationCatchesDuplicateIdsAndNegativePrices() {
        List<ShopEntry> entries = List.of(
                ShopEntry.ability("blue", Material.STONE, "Blue", -1, "jjk:blue", true),
                ShopEntry.item("BLUE", Material.STONE, "Dup", 5, Map.of()));
        List<String> problems = ShopEntry.validate(entries);
        assertTrue(problems.stream().anyMatch(p -> p.contains("Duplicate id")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("Negative price")));
    }

    @Test
    void validationCatchesMissingIconDisplayNameAndItemData() {
        var badItem = new ShopEntry("sword", ShopEntry.Category.ITEM, List.of(), null, " ", 10, true,
                "minecraft:dash_sword", Map.of(), 0);
        List<String> problems = ShopEntry.validate(List.of(badItem));
        assertTrue(problems.stream().anyMatch(p -> p.contains("No icon")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("Blank display name")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("no item data")));
    }
}
