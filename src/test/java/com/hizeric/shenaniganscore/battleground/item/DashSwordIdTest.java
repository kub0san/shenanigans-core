package com.hizeric.shenaniganscore.battleground.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * One-time migration of the Dash Sword catalogue / ownership id from the legacy bare
 * {@code dash_sword} and the intermediate {@code minecraft:dashsword} to the canonical
 * underscored {@code minecraft:dash_sword}. No runtime alias exists.
 */
class DashSwordIdTest {

    @Test
    void canonicalIdUsesUnderscoredNaming() {
        assertEquals("minecraft:dash_sword", DashSwordId.NEW_ID);
    }

    @Test
    void bothLegacySpellingsMigrateToCanonical() {
        assertEquals("minecraft:dash_sword", DashSwordId.migrate("dash_sword"));
        assertEquals("minecraft:dash_sword", DashSwordId.migrate("minecraft:dashsword"));
    }

    @Test
    void migrationIsIdempotent() {
        assertEquals("minecraft:dash_sword", DashSwordId.migrate("minecraft:dash_sword"));
    }

    @Test
    void unrelatedIdsAreUntouched() {
        assertEquals("jjk:blue", DashSwordId.migrate("jjk:blue"));
        assertEquals("battleground:dash_sword", DashSwordId.migrate("battleground:dash_sword"));
        assertEquals("dash_sword_speed", DashSwordId.migrate("dash_sword_speed"));
    }

    @Test
    void nullStaysNull() {
        assertNull(DashSwordId.migrate(null));
    }
}
