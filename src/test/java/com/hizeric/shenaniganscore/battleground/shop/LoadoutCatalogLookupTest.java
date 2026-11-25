package com.hizeric.shenaniganscore.battleground.shop;

import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import com.hizeric.shenaniganscore.battleground.loadout.KitDefaults;
import com.hizeric.shenaniganscore.battleground.loadout.LoadoutPolicy;
import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #65: the real {@link LoadoutPolicy#catalogLookup(ShopCatalog)} adapter must resolve a
 * pre-migration stamp to the canonical enabled item row, without relaxing the row's other gates.
 * The same in-memory database and injected seed pattern as the shop catalogue tests is reused so
 * no live server is involved.
 */
class LoadoutCatalogLookupTest {

    private static final String LEGACY_ID = "dash_sword";
    private static final String CANONICAL_ID = "minecraft:dash_sword";

    private Database database;
    private ShopCatalog catalog;

    @BeforeEach
    void setUp(@TempDir Path temp) throws Exception {
        MockBukkit.mock();
        database = Database.open(temp.resolve("shop.db").toFile());
        catalog = new ShopCatalog(null, database);
        catalog.load(new AbilityRegistry(new ArrayList<>()::add), List.of(canonicalSeed()));
    }

    @AfterEach
    void tearDown() throws Exception {
        database.close();
        MockBukkit.unmock();
    }

    private static ShopEntry canonicalSeed() {
        ItemStack stack = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = stack.getItemMeta();
        BattlegroundItemKeys.stamp(meta, CANONICAL_ID);
        stack.setItemMeta(meta);
        return new ShopEntry(CANONICAL_ID, ShopEntry.Category.ITEM, List.of(),
                Material.NETHERITE_SWORD, "Dash Sword", 350, true,
                CANONICAL_ID, stack.serialize(), 0);
    }

    /** A saved item keeps its own stamp; the catalog row is canonical. */
    private static ItemStack restamp(ItemStack stack, String id) {
        ItemStack copy = stack.clone();
        ItemMeta meta = copy.getItemMeta();
        BattlegroundItemKeys.stamp(meta, id);
        copy.setItemMeta(meta);
        return copy;
    }

    @Test
    void aLegacyStampedCanonicalItemSurvivesSanitizeWhenOwnedAndEnabled() {
        ItemStack[] kit = KitDefaults.items();
        ItemStack[] input = kit.clone();
        ItemStack canonical = LoadoutPolicy.catalogLookup(catalog)
                .enabledCustomItem(CANONICAL_ID).stack();
        input[0] = restamp(canonical, LEGACY_ID); // same deliverable, pre-migration stamp

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, LoadoutPolicy.catalogLookup(catalog), id -> true);

        assertEquals(Material.NETHERITE_SWORD, result.slots()[0].getType(),
                "a legitimate pre-migration item must not be replaced with the kit default");
        assertEquals(LEGACY_ID, BattlegroundItemKeys.idOf(result.slots()[0]));
        assertEquals(0, result.replaced());
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aLegacyStampedMaceIsStillRejectedBySanitize() {
        ItemStack[] kit = KitDefaults.items();
        ItemStack[] input = kit.clone();
        input[0] = restamp(new ItemStack(Material.MACE), LEGACY_ID);

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, LoadoutPolicy.catalogLookup(catalog), id -> true);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType(),
                "forging the stamp onto another item type must not work");
        assertEquals(1, result.replaced());
    }

    @Test
    void aForgedStackOfAKitDefaultIsRejectedBySanitize() {
        ItemStack[] kit = KitDefaults.items();
        ItemStack[] input = kit.clone();
        ItemStack forged = kit[1].clone(); // the bow, amount 1
        forged.setAmount(64);
        input[1] = forged;

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, LoadoutPolicy.catalogLookup(catalog), id -> true);

        assertEquals(kit[1].getAmount(), result.slots()[1].getAmount(),
                "isSimilar ignores amount, so the fallback gate must check it explicitly");
        assertEquals(1, result.replaced());
    }

    @Test
    void anEnabledCustomItemResolvesThroughItsCanonicalId() {
        LoadoutPolicy.CustomItem resolved =
                LoadoutPolicy.catalogLookup(catalog).enabledCustomItem(CANONICAL_ID);

        assertNotNull(resolved);
        assertEquals(CANONICAL_ID, resolved.entryId());
        assertEquals(Material.NETHERITE_SWORD, resolved.stack().getType());
        assertEquals(1, resolved.stack().getAmount());
    }

    @Test
    void aLegacyStampResolvesToTheCanonicalEnabledEntry() {
        LoadoutPolicy.CustomItem resolved =
                LoadoutPolicy.catalogLookup(catalog).enabledCustomItem(LEGACY_ID);

        assertNotNull(resolved, "a pre-migration saved layout must still find its sword");
        assertEquals(CANONICAL_ID, resolved.entryId());
        assertEquals(Material.NETHERITE_SWORD, resolved.stack().getType());
    }

    @Test
    void canonicalisationDoesNotBypassTheEnabledGate() {
        ShopEntry seed = catalog.entry(CANONICAL_ID);
        catalog.entries().set(0, new ShopEntry(seed.id(), seed.category(), seed.tags(), seed.icon(),
                seed.displayName(), seed.price(), false, seed.abilityId(), seed.itemData(),
                seed.streakRequired()));

        assertNull(LoadoutPolicy.catalogLookup(catalog).enabledCustomItem(LEGACY_ID));
    }

    @Test
    void canonicalisationDoesNotBypassTheCustomItemGate() {
        catalog.entries().set(0, new ShopEntry(CANONICAL_ID, ShopEntry.Category.ABILITY, List.of(),
                Material.NETHERITE_SWORD, "Ability", 0, true, CANONICAL_ID, Map.of(), 0));

        assertNull(LoadoutPolicy.catalogLookup(catalog).enabledCustomItem(LEGACY_ID),
                "an ability row with the canonical id is still not a custom item");
    }

    @Test
    void missingAndNullStampsResolveToNothing() {
        LoadoutPolicy.CatalogLookup lookup = LoadoutPolicy.catalogLookup(catalog);

        assertNull(lookup.enabledCustomItem("not_a_real_item"));
        assertNull(lookup.enabledCustomItem(null));
    }
}
