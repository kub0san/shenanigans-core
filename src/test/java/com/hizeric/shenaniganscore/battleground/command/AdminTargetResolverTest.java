package com.hizeric.shenaniganscore.battleground.command;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Resolves an admin-supplied id to a real catalogue id for {@code /bg debug ability|item <id>}.
 *
 * <p>Admins should not have to remember whether an ability is spelled {@code jjk:blue} or
 * {@code blue}, nor which namespace a custom item lives under. The resolver accepts
 * <strong>both id conventions</strong>: an exact namespaced id wins, otherwise a bare value
 * (or a wrong-namespace value) is matched namespace-insensitively against the catalogue's
 * value part. This is the pure decision behind the command; the Bukkit plumbing (performing
 * the ability / handing over the item) is exercised in-game.</p>
 */
class AdminTargetResolverTest {

    private static final List<String> KNOWN = List.of(
            "jjk:blue", "jjk:red", "jjk:fuga", "jjk:piercing_blood", "jjk:purple", "jjk:world_slash",
            "minecraft:dash_sword");

    @Test
    void anExactNamespacedIdResolves() {
        assertEquals("jjk:blue", AdminTargetResolver.resolveAbility("jjk:blue", KNOWN));
        assertEquals("minecraft:dash_sword",
                AdminTargetResolver.resolveAbility("minecraft:dash_sword", KNOWN));
    }

    @Test
    void aBareValueResolvesNamespaceInsensitively() {
        assertEquals("jjk:blue", AdminTargetResolver.resolveAbility("blue", KNOWN));
        assertEquals("jjk:piercing_blood", AdminTargetResolver.resolveAbility("piercing_blood", KNOWN));
        assertEquals("minecraft:dash_sword", AdminTargetResolver.resolveAbility("dash_sword", KNOWN));
    }

    @Test
    void aWrongNamespaceStillResolvesByValue() {
        // Admin types the item namespace out of habit; the value is what identifies the ability.
        assertEquals("jjk:red", AdminTargetResolver.resolveAbility("minecraft:red", KNOWN));
    }

    @Test
    void matchingIsCaseInsensitive() {
        assertEquals("jjk:blue", AdminTargetResolver.resolveAbility("JJK:BLUE", KNOWN));
        assertEquals("jjk:blue", AdminTargetResolver.resolveAbility("Blue", KNOWN));
    }

    @Test
    void surroundingWhitespaceIsIgnored() {
        assertEquals("jjk:blue", AdminTargetResolver.resolveAbility("  jjk:blue  ", KNOWN));
    }

    @Test
    void anUnknownIdResolvesToNull() {
        assertNull(AdminTargetResolver.resolveAbility("does_not_exist", KNOWN));
        assertNull(AdminTargetResolver.resolveAbility("jjk:unknown", KNOWN));
    }

    @Test
    void aBlankOrNullResolvesToNull() {
        assertNull(AdminTargetResolver.resolveAbility(null, KNOWN));
        assertNull(AdminTargetResolver.resolveAbility("", KNOWN));
        assertNull(AdminTargetResolver.resolveAbility("   ", KNOWN));
    }

    @Test
    void tabCompletionOffersBareValuesInsteadOfNamespacedIds() {
        // The completion must match what an admin actually types; the resolver maps it back
        // to the canonical id on execution. Namespaced ids stay in the no-argument listing.
        assertEquals(
                List.of("blue", "red", "fuga", "piercing_blood", "purple", "world_slash", "dash_sword"),
                AdminTargetResolver.bareValueIds(KNOWN));
    }

    @Test
    void tabCompletionLeavesAnUnnamespacedIdUntouched() {
        assertEquals(List.of("plain"), AdminTargetResolver.bareValueIds(List.of("plain")));
    }

    @Test
    void aCustomItemIdResolvesToItsShopRow() {
        // The dash sword's shop id is minecraft:dash_sword; an admin may type either the
        // full id or just the value.
        List<String> itemIds = List.of("minecraft:dash_sword");
        assertEquals("minecraft:dash_sword",
                AdminTargetResolver.resolveItem("minecraft:dash_sword", itemIds));
        assertEquals("minecraft:dash_sword",
                AdminTargetResolver.resolveItem("dash_sword", itemIds));
    }
}