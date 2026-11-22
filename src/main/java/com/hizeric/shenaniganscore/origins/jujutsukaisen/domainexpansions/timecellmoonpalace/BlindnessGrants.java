package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Pure bookkeeping of the players a Time Cell {@code cutToBlack} flash blinded.
 *
 * <p>{@code Session} records a grant for every player it applies BLINDNESS to,
 * then on {@code close()} removes the effect from exactly {@link #playersToClear()}
 * — the set it blinded, never a stranger's blindness — and {@link #clear() clears}
 * the record so the next session starts clean. No Bukkit types, so the rule is
 * unit-tested by {@code BlindnessGrantsTest}.</p>
 */
public final class BlindnessGrants {

    private final Set<UUID> granted = new LinkedHashSet<>();

    /** Records that this session blinded the given player. A null id is ignored. */
    public void grant(UUID player) {
        if (player != null) granted.add(player);
    }

    /** The players to remove blindness from on close — exactly those this session blinded. */
    public Set<UUID> playersToClear() {
        return Set.copyOf(granted);
    }

    /** Empties the grant so the record cannot leak into a later session. */
    public void clear() {
        granted.clear();
    }
}