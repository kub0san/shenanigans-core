package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The held-item seam: a custom item carries its own ability, rail slot 0 mirrors it, and a cast is
 * only legal while that exact item is in the player's main hand.
 *
 * <p>No Bukkit server is needed here — the catalog row and the rail are plain data.</p>
 */
class HeldItemAbilityTest {

    private static final AbilityId DASH = AbilityId.of("minecraft:dash_sword");

    private static ShopEntry dashSword() {
        return ShopEntry.customItem("dash_sword", Material.IRON_SWORD, "Dash Sword", 350,
                DASH.toString(), Map.of("type", "IRON_SWORD"));
    }

    @Test
    void anItemRowCarriesItsAbility() {
        assertEquals(DASH, HeldItemAbility.of(dashSword()));
    }

    @Test
    void aPlainAbilityRowCarriesNoHeldItemAbility() {
        assertNull(HeldItemAbility.of(ShopEntry.ability(
                "blue", Material.END_ROD, "Blue", 40, "jjk:blue", true)));
        assertNull(HeldItemAbility.of(null));
    }

    @Test
    void aMalformedCatalogAbilityIdHidesTheSlotInsteadOfThrowing() {
        ShopEntry broken = ShopEntry.customItem("dash_sword", Material.IRON_SWORD, "Dash Sword",
                350, ":", Map.of("type", "IRON_SWORD"));

        assertNull(HeldItemAbility.of(broken));
    }

    @Test
    void bindingPutsTheItemAbilityInSlotZero() {
        AbilityRail rail = new AbilityRail();

        assertTrue(HeldItemAbility.bind(rail, DASH));

        assertEquals(DASH, rail.get(0));
    }

    @Test
    void rebindingReplacesSlotZeroInsteadOfStacking() {
        AbilityRail rail = new AbilityRail();
        HeldItemAbility.bind(rail, DASH);

        AbilityId other = AbilityId.of("battleground:other");
        HeldItemAbility.bind(rail, other);

        assertEquals(other, rail.get(0));
    }

    @Test
    void bindingNothingClearsSlotZero() {
        AbilityRail rail = new AbilityRail();
        HeldItemAbility.bind(rail, DASH);

        assertTrue(HeldItemAbility.bind(rail, null));

        assertNull(rail.get(0));
    }

    @Test
    void bindingAnUnchangedAbilityIsNotADirtyWrite() {
        AbilityRail rail = new AbilityRail();
        HeldItemAbility.bind(rail, DASH);

        assertFalse(HeldItemAbility.bind(rail, DASH));
    }

    @Test
    void bindingNeverTouchesTheNormalOrUltimateSlots() {
        AbilityRail rail = new AbilityRail();
        AbilityId normal = AbilityId.of("jjk:blue");
        AbilityId ultimate = AbilityId.of("jjk:purple");
        rail.set(AbilityRail.NORMAL_SLOT, normal);
        rail.set(AbilityRail.ULTIMATE_SLOT, ultimate);

        HeldItemAbility.bind(rail, DASH);

        assertEquals(normal, rail.get(AbilityRail.NORMAL_SLOT));
        assertEquals(ultimate, rail.get(AbilityRail.ULTIMATE_SLOT));
    }

    @Test
    void aCastOnlyMatchesWhileTheMatchingItemIsHeld() {
        assertTrue(HeldItemAbility.matches(DASH, DASH));
        assertFalse(HeldItemAbility.matches(DASH, null),
                "a plain item must not cast the bound item ability");
        assertFalse(HeldItemAbility.matches(null, DASH),
                "nothing bound means nothing to cast");
        assertFalse(HeldItemAbility.matches(DASH, AbilityId.of("battleground:other")));
    }

    @Test
    void syncPromotesTheOldHeldAbilityWhenTheNormalSlotIsFree() {
        AbilityRail rail = new AbilityRail();
        HeldItemAbility.sync(rail, DASH);

        HeldItemAbility.SyncResult result = HeldItemAbility.sync(rail, null);

        assertTrue(result.changed());
        assertTrue(result.promotedToNormal());
        assertEquals(DASH, result.previous());
        assertNull(result.next());
        assertEquals(DASH, rail.normal());
        assertNull(rail.held());
    }

    @Test
    void syncDoesNotOverwriteAnExistingNormalBind() {
        AbilityRail rail = new AbilityRail();
        AbilityId blue = AbilityId.of("jjk:blue");
        rail.set(AbilityRail.NORMAL_SLOT, blue);
        HeldItemAbility.sync(rail, DASH);

        HeldItemAbility.SyncResult result = HeldItemAbility.sync(rail, null);

        assertFalse(result.promotedToNormal());
        assertEquals(blue, rail.normal());
    }

    @Test
    void syncReportsNoChangeForTheSameHeldAbility() {
        AbilityRail rail = new AbilityRail();
        HeldItemAbility.sync(rail, DASH);

        HeldItemAbility.SyncResult result = HeldItemAbility.sync(rail, DASH);

        assertFalse(result.changed());
        assertFalse(result.promotedToNormal());
    }
}
