package com.hizeric.shenaniganscore.battleground.ability;

import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import com.hizeric.shenaniganscore.core.ability.AbilityId;

import java.util.Objects;

/**
 * The held-item seam: a custom item owns its ability, slot 0 mirrors it, and a cast is only legal
 * while that exact item is in the player's main hand.
 *
 * <p>Pure rules only — no Bukkit player, inventory or catalog state. When the held ability goes
 * away and the normal slot is free, the previous held ability is promoted there rather than lost.</p>
 */
public final class HeldItemAbility {

    /** Rail slot 0 is the held custom item's ability. */
    public static final int SLOT = AbilityRail.HELD_SLOT;

    public record SyncResult(AbilityId previous, AbilityId next, boolean changed,
                             boolean promotedToNormal) { }

    private HeldItemAbility() { }

    /** The ability a catalog row carries, or null when the row is not a custom item with one. */
    public static AbilityId of(ShopEntry entry) {
        if (entry == null || !entry.isCustomItem()) return null;
        String abilityId = entry.abilityId();
        if (abilityId == null || abilityId.isBlank()) return null;
        try {
            return AbilityId.of(abilityId);
        } catch (IllegalArgumentException malformed) {
            // This reads shop.yml every HUD tick; a bad admin value must hide the slot, not throw.
            return null;
        }
    }

    /**
     * Mirrors the held ability into slot 0. When the ability changes and slot 1 is empty, the old
     * held ability is promoted into slot 1 so it stays castable after the item is released.
     */
    public static SyncResult sync(AbilityRail rail, AbilityId itemAbility) {
        Objects.requireNonNull(rail, "rail");
        AbilityId previous = rail.held();
        boolean promote = previous != null && !previous.equals(itemAbility)
                && rail.normal() == null;
        if (promote) rail.set(AbilityRail.NORMAL_SLOT, previous);
        rail.set(SLOT, itemAbility);
        boolean changed = promote || !Objects.equals(previous, itemAbility);
        return new SyncResult(previous, itemAbility, changed, promote);
    }

    /** Legacy shim: returns only whether the rail changed. */
    public static boolean bind(AbilityRail rail, AbilityId itemAbility) {
        return sync(rail, itemAbility).changed();
    }

    /** True only when a cast would be backed by the item the player is actually holding. */
    public static boolean matches(AbilityId bound, AbilityId heldAbility) {
        return bound != null && heldAbility != null && bound.equals(heldAbility);
    }
}
