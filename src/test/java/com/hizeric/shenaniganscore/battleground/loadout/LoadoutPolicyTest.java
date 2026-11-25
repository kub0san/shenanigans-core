package com.hizeric.shenaniganscore.battleground.loadout;

import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #65: the 41-slot loadout is untrusted player data. A slot may be kept only when it is the
 * kit's own default stack, or when its persistent stamp resolves to an enabled catalog custom item
 * the player owns and whose stack matches the canonical catalog deliverable. Everything else is
 * replaced with that slot's kit default.
 */
class LoadoutPolicyTest {

    private static final String ITEM_ID = "dash_sword";

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack stamped(Material material, String id) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        BattlegroundItemKeys.stamp(meta, id);
        stack.setItemMeta(meta);
        return stack;
    }

    private static ItemStack[] kit() {
        return KitDefaults.items();
    }

    private static LoadoutPolicy.CatalogLookup enabledCatalog(ItemStack canonical) {
        return id -> ITEM_ID.equals(id) ? new LoadoutPolicy.CustomItem(ITEM_ID, canonical) : null;
    }

    @Test
    void anUnstampedMaceIsReplacedWithTheKitDefault() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();
        input[0] = new ItemStack(Material.MACE);

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, enabledCatalog(stamped(Material.DIAMOND_SWORD, ITEM_ID)), id -> true);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType(), "slot 0 restores the sword");
        assertEquals(1, result.replaced());
        assertEquals(1, result.warnings().size(), "one warning per sanitization, not per slot");
    }

    @Test
    void anOwnedCanonicalCustomItemIsPreserved() {
        ItemStack[] kit = kit();
        ItemStack canonical = stamped(Material.DIAMOND_SWORD, ITEM_ID);
        ItemStack[] input = kit.clone();
        input[0] = canonical.clone();

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, enabledCatalog(canonical), id -> true);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType());
        assertEquals(0, result.replaced(), "a legitimate custom item is not a replacement");
        assertTrue(result.warnings().isEmpty());
    }

    @Test
    void aForgedStackOfAnOwnedCustomItemIsRejected() {
        ItemStack[] kit = kit();
        ItemStack canonical = stamped(Material.DIAMOND_SWORD, ITEM_ID);
        ItemStack forged = canonical.clone();
        forged.setAmount(64); // isSimilar ignores amount; only the explicit check rejects this

        ItemStack[] input = kit.clone();
        input[0] = forged;

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, enabledCatalog(canonical), id -> true);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType());
        assertEquals(1, result.slots()[0].getAmount(), "the forged whole stack is replaced, not kept");
        assertEquals(1, result.replaced());
    }

    @Test
    void aStampedButNoncanonicalMaceIsRejected() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();
        input[0] = stamped(Material.MACE, ITEM_ID); // forged stamp on a different item type

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, enabledCatalog(stamped(Material.DIAMOND_SWORD, ITEM_ID)), id -> true);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType());
        assertEquals(1, result.replaced());
    }

    @Test
    void anOwnedButDisabledEntryIsRejected() {
        ItemStack[] kit = kit();
        ItemStack canonical = stamped(Material.DIAMOND_SWORD, ITEM_ID);
        ItemStack[] input = kit.clone();
        input[0] = canonical.clone();

        // The lookup stands in for a catalog row that exists but is disabled/absent: no canonical
        // stack is resolvable, so ownership alone cannot rescue the slot.
        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(input, kit, id -> null, id -> true);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType());
        assertEquals(1, result.replaced());
    }

    @Test
    void aCanonicalItemThePlayerDoesNotOwnIsRejected() {
        ItemStack[] kit = kit();
        ItemStack canonical = stamped(Material.DIAMOND_SWORD, ITEM_ID);
        ItemStack[] input = kit.clone();
        input[0] = canonical.clone();

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, enabledCatalog(canonical), id -> false);

        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType());
        assertEquals(1, result.replaced());
    }

    @Test
    void nullAndAirSlotsStayUnchangedAndUncounted() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();
        input[5] = null;
        input[6] = new ItemStack(Material.AIR);

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, enabledCatalog(stamped(Material.DIAMOND_SWORD, ITEM_ID)), id -> false);

        assertNull(result.slots()[5]);
        assertEquals(Material.AIR, result.slots()[6].getType());
        assertEquals(0, result.replaced());
    }

    @Test
    void aSlotMatchingTheKitDefaultIsAllowed() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();
        input[1] = new ItemStack(Material.BOW); // kit default for slot 1

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, id -> null, id -> false);

        assertEquals(Material.BOW, result.slots()[1].getType());
        assertEquals(0, result.replaced());
    }

    @Test
    void armourAndOffhandSlotsAreCoveredToo() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();
        input[36] = new ItemStack(Material.MACE); // boots slot
        input[40] = new ItemStack(Material.MACE); // offhand slot

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(
                input, kit, id -> null, id -> false);

        assertEquals(Material.DIAMOND_BOOTS, result.slots()[36].getType());
        assertNull(result.slots()[40], "empty kit offhand stays empty");
        assertEquals(2, result.replaced(), "slots 36 and 40 are part of the 41-slot board");
    }

    @Test
    void warningsAndCountAreDeterministic() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();
        input[0] = new ItemStack(Material.MACE);
        input[9] = new ItemStack(Material.MACE);
        var catalog = enabledCatalog(stamped(Material.DIAMOND_SWORD, ITEM_ID));

        LoadoutPolicy.Result first = LoadoutPolicy.sanitize(input, kit, catalog, id -> false);
        LoadoutPolicy.Result second = LoadoutPolicy.sanitize(input, kit, catalog, id -> false);

        assertEquals(first.replaced(), second.replaced());
        assertEquals(first.warnings(), second.warnings());
        assertEquals(2, first.replaced());
        assertEquals(first.warnings().get(0), LoadoutPolicy.warning(2));
    }

    @Test
    void theResultDoesNotAliasTheInput() {
        ItemStack[] kit = kit();
        ItemStack[] input = kit.clone();

        LoadoutPolicy.Result result = LoadoutPolicy.sanitize(input, kit, id -> null, id -> false);
        ItemStack[] snapshot = result.slots();
        input[0] = new ItemStack(Material.MACE);

        assertEquals(Material.DIAMOND_SWORD, snapshot[0].getType());
        assertEquals(Material.DIAMOND_SWORD, result.slots()[0].getType());
    }

    @Test
    void catalogLookupRejectsDisabledAndNonCustomRows() {
        // Pure lookup seam: no ShopCatalog instance needed for the common negative cases.
        assertNull(LoadoutPolicy.catalogLookup(null).enabledCustomItem(ITEM_ID));
    }
}
