package com.hizeric.shenaniganscore.battleground.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The market seam: category filtering, pagination over the filtered catalogue, and the
 * per-row display data (price, ownership, streak requirement and progress) the GUI renders.
 * No Bukkit runtime is required — the GUI is a thin renderer over these rows.
 */
class MarketViewTest {

    private static final int PER_PAGE = 12;

    /** Ownership predicate backed by a set of lower-cased entry ids, matching the player store. */
    private static Predicate<ShopEntry> owns(Set<String> owned) {
        return entry -> owned.contains(entry.id().toLowerCase(Locale.ROOT));
    }

    private static List<ShopEntry> catalogue() {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(ShopEntry.customItem("dash", Material.FEATHER, "Dash Sword", 30, 0, java.util.Map.of()));
        entries.add(ShopEntry.ability("blue", Material.LIGHT_BLUE_CONCRETE, "Blue", 40, 5, "jjk:blue"));
        entries.add(ShopEntry.ultimate("void", Material.END_CRYSTAL, "Unlimited Void", 100, 10, "jjk:void"));
        return entries;
    }

    @Test
    void eachFilterShowsOnlyMatchingEntries() {
        List<ShopEntry> entries = catalogue();

        assertEquals(1, MarketView.page(entries, ShopEntry.Filter.CUSTOM_ITEMS, 0, PER_PAGE, 0, owns(Set.of())).size());
        assertEquals(1, MarketView.page(entries, ShopEntry.Filter.ABILITIES, 0, PER_PAGE, 0, owns(Set.of())).size());
        assertEquals(1, MarketView.page(entries, ShopEntry.Filter.ULTIMATES, 0, PER_PAGE, 0, owns(Set.of())).size());
        assertEquals(3, MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 0, owns(Set.of())).size());

        assertTrue(MarketView.page(entries, ShopEntry.Filter.CUSTOM_ITEMS, 0, PER_PAGE, 0, owns(Set.of()))
                .stream().allMatch(row -> row.entry().isCustomItem()));
        assertTrue(MarketView.page(entries, ShopEntry.Filter.ABILITIES, 0, PER_PAGE, 0, owns(Set.of()))
                .stream().allMatch(row -> row.entry().isAbility()));
    }

    @Test
    void paginationExposesLaterPagesOfTheCatalogue() {
        List<ShopEntry> entries = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            entries.add(ShopEntry.customItem("item-" + i, Material.DIAMOND_SWORD, "Sword " + i, 10 + i, 0,
                    java.util.Map.of()));
        }

        assertEquals(2, MarketView.pager(entries, ShopEntry.Filter.ALL, 0, PER_PAGE).maxPages());

        List<MarketView.Row> first = MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 0, owns(Set.of()));
        List<MarketView.Row> second = MarketView.page(entries, ShopEntry.Filter.ALL, 1, PER_PAGE, 0, owns(Set.of()));

        assertEquals(PER_PAGE, first.size());
        assertEquals(3, second.size(), "the second page holds the 3 entries the first page could not fit");
        assertEquals("Sword 12", second.get(0).entry().displayName(), "page 2 starts where page 1 ended");
    }

    @Test
    void displayedPriceComesFromTheHardcodedCatalogue() {
        List<ShopEntry> entries = catalogue();

        for (MarketView.Row row : MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 0, owns(Set.of()))) {
            assertEquals(row.entry().price(), row.price(), "the market shows the catalogue price verbatim");
        }
    }

    @Test
    void lockedEntriesExposePriceRequirementAndProgress() {
        List<ShopEntry> entries = List.of(
                ShopEntry.ability("blue", Material.LIGHT_BLUE_CONCRETE, "Blue", 40, 5, "jjk:blue"));

        // Exercise the requirements-on path explicitly; the shipped-off policy is covered by
        // KillstreakToggleTest. This keeps the test deterministic regardless of global policy state.
        MarketView.Row row = MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 2, owns(Set.of()), true)
                .get(0);

        assertFalse(row.owned());
        assertEquals(40, row.price());
        assertEquals(5, row.streakRequired());
        assertEquals(2, row.streakProgress(), "progress is the current streak clamped to the requirement");
        assertFalse(row.streakMet());
        assertFalse(row.usable(), "an unowned entry is never usable, whatever the streak");
        assertTrue(row.locked());
    }

    @Test
    void aBoughtCustomItemIsUsableWithoutAStreak() {
        List<ShopEntry> entries = List.of(
                ShopEntry.customItem("dash", Material.FEATHER, "Dash Sword", 30, 0, java.util.Map.of()));

        MarketView.Row row = MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 0, owns(Set.of("dash")))
                .get(0);

        assertTrue(row.owned());
        assertEquals(0, row.streakRequired(), "custom items carry no streak gate");
        assertTrue(row.usable(), "a bought custom item works from deploy");
        assertFalse(row.locked());
    }

    @Test
    void anOwnedAbilityStillNeedsItsLiveStreak() {
        List<ShopEntry> entries = List.of(
                ShopEntry.ability("blue", Material.LIGHT_BLUE_CONCRETE, "Blue", 40, 3, "jjk:blue"));

        // Requirements-on path; the shipped-off policy is covered by KillstreakToggleTest.
        MarketView.Row row = MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 1, owns(Set.of("blue")), true)
                .get(0);

        assertTrue(row.owned(), "ownership is permanent");
        assertEquals(3, row.streakRequired());
        assertEquals(1, row.streakProgress());
        assertFalse(row.streakMet());
        assertFalse(row.usable(), "an owned ability still needs the live killstreak to cast");
    }

    @Test
    void disabledEntriesNeverReachTheMarket() {
        List<ShopEntry> entries = new ArrayList<>(catalogue());
        entries.add(new ShopEntry("retired", ShopEntry.Category.ABILITY, List.of(), Material.BARRIER,
                "Retired", 5, false, "jjk:retired", java.util.Map.of(), 0));

        List<MarketView.Row> rows = MarketView.page(entries, ShopEntry.Filter.ALL, 0, PER_PAGE, 0, owns(Set.of()));

        assertEquals(3, rows.size());
        assertTrue(rows.stream().noneMatch(row -> row.entry().id().equals("retired")));
    }

    @Test
    void pageBeyondTheEndClampsToTheLastPage() {
        List<ShopEntry> entries = List.of(
                ShopEntry.customItem("dash", Material.FEATHER, "Dash Sword", 30, 0, java.util.Map.of()));

        List<MarketView.Row> rows = MarketView.page(entries, ShopEntry.Filter.ALL, 9, PER_PAGE, 0, owns(Set.of()));

        assertEquals(1, rows.size(), "an out-of-range page still returns the catalogue, not an empty list");
    }
}