package com.hizeric.shenaniganscore.battleground.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** One-time translation of legacy {@code unlocked} ability ids into catalog entry ids. */
class LegacyOwnershipTest {

    @Test
    void namespacedAbilityBecomesItsCatalogValue() {
        assertEquals("blue", LegacyOwnership.toCatalogEntry("jjk:blue"));
        assertEquals("red", LegacyOwnership.toCatalogEntry("jjk:red"));
        assertEquals("purple", LegacyOwnership.toCatalogEntry("jjk:purple"));
        assertEquals("fuga", LegacyOwnership.toCatalogEntry("jjk:fuga"));
    }

    @Test
    void legacyDashAbilityMapsToTheCanonicalItemId() {
        assertEquals("minecraft:dash_sword", LegacyOwnership.toCatalogEntry("battleground:dash_sword"));
        assertEquals("minecraft:dash_sword", LegacyOwnership.toCatalogEntry("minecraft:dashsword"));
    }

    @Test
    void legacyExecutionerAbilityMapsToTheCanonicalItemId() {
        assertEquals("minecraft:executioners_sword",
                LegacyOwnership.toCatalogEntry("minecraft:executioner_sword"));
        assertEquals("minecraft:executioners_sword",
                LegacyOwnership.toCatalogEntry("executioner_sword"));
        assertEquals("minecraft:executioners_sword",
                LegacyOwnership.toCatalogEntry("minecraft:executionsword"));
        assertEquals("minecraft:executioners_sword",
                LegacyOwnership.toCatalogEntry("minecraft:executioners_sword"));
    }

    @Test
    void aBareValueIsAlreadyAnEntryId() {
        assertEquals("blue", LegacyOwnership.toCatalogEntry("blue"));
    }

    @Test
    void caseAndWhitespaceAreNormalised() {
        assertEquals("blue", LegacyOwnership.toCatalogEntry("  JJK:Blue  "));
    }

    @Test
    void blankIsIgnored() {
        assertNull(LegacyOwnership.toCatalogEntry(null));
        assertNull(LegacyOwnership.toCatalogEntry(""));
        assertNull(LegacyOwnership.toCatalogEntry("   "));
    }
}