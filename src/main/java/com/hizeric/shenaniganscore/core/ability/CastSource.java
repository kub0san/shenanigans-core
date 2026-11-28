package com.hizeric.shenaniganscore.core.ability;

/** How a cast was initiated. Player-reachable sources share the same cast funnel. */
public enum CastSource {
    /** {@code /ability 1..3} — the universal fallback. */
    COMMAND,
    /** A held custom item (right-click) submits a cast for its rail slot. */
    ITEM,
    /**
     * Admin debug cast from {@code /bg debug ability <id>}: gateless, cooldown-free, and no
     * {@code TechniqueFx.burst} — mirrors the old {@code AbilityDefinition.perform}-only behavior
     * the debug tools had before routing through the caster.
     */
    DEBUG
}
