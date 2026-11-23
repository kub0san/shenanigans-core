package com.hizeric.shenaniganscore.battleground.shop;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The catalogue-seeding seam: the built-in custom item must replace an ability row for the same
 * ability, otherwise a fresh server ships a free standalone dash instead of the Dash Sword.
 */
class ShopCatalogSeedTest {

    private static final String ABILITY_ID = "minecraft:dash_sword";
    private static final String ITEM_ID = "minecraft:dash_sword";

    private static ShopEntry seededSword(int price) {
        return ShopEntry.customItem(ITEM_ID, Material.NETHERITE_SWORD, "Dash Sword", price,
                ABILITY_ID, Map.of("type", "NETHERITE_SWORD"));
    }

    private static ShopEntry staleSeedAbility() {
        return ShopEntry.ability("dash_sword", Material.NETHERITE_SWORD, "Dash", 0, 0, ABILITY_ID);
    }

    private static ShopEntry legacyItem(int price) {
        return ShopEntry.customItem("dash_sword", Material.NETHERITE_SWORD, "Dash Sword", price,
                ABILITY_ID, Map.of("type", "OLD_STAMP"));
    }

    @Test
    void aStaleAbilityRowIsReplacedByTheCustomItem() {
        ShopEntry blue = ShopEntry.ability("blue", Material.END_ROD, "Blue", 40, 0, "jjk:blue");
        List<ShopEntry> entries = new ArrayList<>(List.of(staleSeedAbility(), blue));

        assertTrue(ShopCatalog.upsertAll(entries, List.of(seededSword(350))));

        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.isCustomItem() && ITEM_ID.equals(e.id())));
        assertTrue(entries.stream().noneMatch(e -> e.isAbility() && "dash_sword".equals(e.id())));
        assertTrue(entries.contains(blue), "unrelated entries must survive the merge");
    }

    @Test
    void aFreshCatalogueGainsTheItemExactlyOnce() {
        List<ShopEntry> entries = new ArrayList<>();

        assertTrue(ShopCatalog.upsertAll(entries, List.of(seededSword(350))));
        assertEquals(1, entries.size());

        assertFalse(ShopCatalog.upsertAll(entries, List.of(seededSword(350))));
        assertEquals(1, entries.size(), "the merge must be idempotent");
    }

    @Test
    void adminEditsToAnOwnedItemRowAreKept() {
        List<ShopEntry> entries = new ArrayList<>(List.of(seededSword(17)));

        assertFalse(ShopCatalog.upsertAll(entries, List.of(seededSword(350))));

        assertEquals(17, entries.get(0).price());
    }

    @Test
    void seededItemPayloadIsRefreshedWhileKeepingAdminEdits() {
        ShopEntry stalePayload = new ShopEntry(ITEM_ID, ShopEntry.Category.ITEM,
                List.of("custom-tag"), Material.STONE, "Admin Renamed", 17, false,
                ABILITY_ID, Map.of("type", "OLD_PAYLOAD"), 9);
        List<ShopEntry> entries = new ArrayList<>(List.of(stalePayload));

        assertTrue(ShopCatalog.upsertAll(entries, List.of(seededSword(350))));

        ShopEntry refreshed = entries.get(0);
        assertEquals(ITEM_ID, refreshed.id());
        assertEquals(Map.of("type", "NETHERITE_SWORD"), refreshed.itemData(),
                "code-owned item payload must be refreshed from the seed");
        assertEquals(Material.NETHERITE_SWORD, refreshed.icon(),
                "code-owned icon must follow the seed");
        assertEquals(17, refreshed.price(), "admin price must survive refresh");
        assertEquals("Admin Renamed", refreshed.displayName(),
                "admin display name must survive refresh");
        assertFalse(refreshed.enabled(), "admin enabled flag must survive refresh");
        assertEquals(9, refreshed.streakRequired(), "admin streak gate must survive refresh");
        assertEquals(List.of("custom-tag"), refreshed.tags(), "admin tags must survive refresh");
    }

    @Test
    void theIntermediateNamespacedIdIsMigratedToo() {
        ShopEntry previous = new ShopEntry("minecraft:dashsword", ShopEntry.Category.ITEM,
                List.of(), Material.NETHERITE_SWORD, "Dash Sword", 350, true,
                "minecraft:dash_sword", Map.of("type", "OLD_STAMP"), 0);
        List<ShopEntry> entries = new ArrayList<>(List.of(previous));

        assertTrue(ShopCatalog.migrateLegacyIds(entries, List.of(seededSword(350))));
        assertEquals(ITEM_ID, entries.get(0).id());
        assertEquals(Map.of("type", "NETHERITE_SWORD"), entries.get(0).itemData());
    }

    @Test
    void aLegacyDashSwordIdIsMigratedToTheCanonicalId() {
        List<ShopEntry> entries = new ArrayList<>(List.of(legacyItem(17)));
        ShopEntry seed = seededSword(350);

        assertTrue(ShopCatalog.migrateLegacyIds(entries, List.of(seed)));
        assertEquals(1, entries.size());
        ShopEntry migrated = entries.get(0);
        assertTrue(migrated.isCustomItem());
        assertEquals(ITEM_ID, migrated.id());
        assertEquals(17, migrated.price(), "admin's price must survive the migration");
        assertEquals(Map.of("type", "NETHERITE_SWORD"), migrated.itemData(),
                "item data must be refreshed from the seed so the PDC stamp matches the new id");
    }

    @Test
    void catalogueMigrationIsIdempotent() {
        List<ShopEntry> entries = new ArrayList<>(List.of(seededSword(350)));

        assertFalse(ShopCatalog.migrateLegacyIds(entries, List.of(seededSword(350))));
        assertEquals(ITEM_ID, entries.get(0).id());
    }

    @Test
    void migrationLeavesUnrelatedRowsUntouched() {
        ShopEntry blue = ShopEntry.ability("blue", Material.END_ROD, "Blue", 40, 0, "jjk:blue");
        List<ShopEntry> entries = new ArrayList<>(List.of(legacyItem(350), blue));

        assertTrue(ShopCatalog.migrateLegacyIds(entries, List.of(seededSword(350))));
        assertTrue(entries.contains(blue), "unrelated entries must survive the id migration");
    }

    private static final String EXECUTIONER_ID = "minecraft:executioners_sword";

    private static ShopEntry seededExecutioner(int price) {
        return ShopEntry.customItem(EXECUTIONER_ID, Material.NETHERITE_SWORD, "Executioner's Sword",
                price, EXECUTIONER_ID, Map.of("type", "NETHERITE_SWORD"));
    }

    private static ShopEntry staleExecutioner(int price) {
        return ShopEntry.customItem("minecraft:executioner_sword", Material.NETHERITE_SWORD,
                "Executioner's Sword", price, "minecraft:executioner_sword",
                Map.of("type", "OLD_STAMP"));
    }

    @Test
    void aStaleExecutionerRowIsMigratedAndRefreshedFromTheSeed() {
        List<ShopEntry> entries = new ArrayList<>(List.of(staleExecutioner(500)));

        assertTrue(ShopCatalog.migrateLegacyIds(entries, List.of(seededExecutioner(500))));
        assertEquals(1, entries.size());
        ShopEntry migrated = entries.get(0);
        assertEquals(EXECUTIONER_ID, migrated.id());
        assertEquals(EXECUTIONER_ID, migrated.abilityId());
        assertEquals(Map.of("type", "NETHERITE_SWORD"), migrated.itemData(),
                "the stamp must be refreshed so old rows stop resolving to an unregistered ability");
    }

    @Test
    void aStaleExecutionerDuplicateIsDroppedWhenTheCanonicalRowExists() {
        List<ShopEntry> entries = new ArrayList<>(List.of(
                staleExecutioner(500), seededExecutioner(500)));

        assertTrue(ShopCatalog.migrateLegacyIds(entries, List.of(seededExecutioner(500))));
        assertEquals(1, entries.size());
        assertEquals(EXECUTIONER_ID, entries.get(0).id());
    }

    @Test
    void legacyExecutionerIdsResolveToTheCanonicalItemId() {
        assertEquals(EXECUTIONER_ID, ShopCatalog.canonicalCustomItemId("minecraft:executioner_sword"));
        assertEquals(EXECUTIONER_ID, ShopCatalog.canonicalCustomItemId("executioner_sword"));
        assertEquals(EXECUTIONER_ID, ShopCatalog.canonicalCustomItemId("minecraft:executionsword"));
        assertEquals(EXECUTIONER_ID, ShopCatalog.canonicalCustomItemId(EXECUTIONER_ID));
    }

    private static ShopEntry newAbility() {
        return ShopEntry.ability("worldslash", Material.NETHERITE_SCRAP, "World Cutting Slash",
                250, 6, "jjk:world_slash");
    }

    @Test
    void aNewlyConnectedAbilityReachesAnExistingCatalogue() {
        ShopEntry blue = ShopEntry.ability("blue", Material.END_ROD, "Blue", 40, 0, "jjk:blue");
        List<ShopEntry> entries = new ArrayList<>(List.of(blue));

        assertTrue(ShopCatalog.upsertAbilities(entries, List.of(newAbility())));
        assertEquals(2, entries.size());
        ShopEntry added = entries.stream()
                .filter(e -> "jjk:world_slash".equals(e.abilityId())).findFirst().orElseThrow();
        assertTrue(added.isAbility());
        assertEquals(250, added.price());
        assertEquals(6, added.streakRequired());
        assertTrue(entries.contains(blue), "existing rows must survive the merge");
    }

    @Test
    void anExistingAbilityRowKeepsAdminEdits() {
        ShopEntry edited = ShopEntry.ability("worldslash", Material.NETHERITE_SCRAP,
                "Custom name", 17, 1, "jjk:world_slash");
        List<ShopEntry> entries = new ArrayList<>(List.of(edited));

        assertFalse(ShopCatalog.upsertAbilities(entries, List.of(newAbility())));
        assertEquals(1, entries.size());
        assertEquals(17, entries.get(0).price());
        assertEquals("Custom name", entries.get(0).displayName());
    }

    @Test
    void aBareStoredAbilityIdStillMatchesTheNamespacedSeed() {
        // Older rows store the bare value; they must not cause a duplicate row.
        ShopEntry legacy = ShopEntry.ability("worldslash", Material.NETHERITE_SCRAP,
                "World Cutting Slash", 250, 6, "world_slash");
        List<ShopEntry> entries = new ArrayList<>(List.of(legacy));

        assertFalse(ShopCatalog.upsertAbilities(entries, List.of(newAbility())));
        assertEquals(1, entries.size());
    }

    @Test
    void abilityUpsertIsIdempotent() {
        List<ShopEntry> entries = new ArrayList<>();

        assertTrue(ShopCatalog.upsertAbilities(entries, List.of(newAbility())));
        assertFalse(ShopCatalog.upsertAbilities(entries, List.of(newAbility())));
        assertEquals(1, entries.size());
    }
}
