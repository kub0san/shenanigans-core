package com.hizeric.shenaniganscore.core.ability;

/** How a cast was initiated. The battleground exposes a single command-driven cast path. */
public enum CastSource {
    /** {@code /ability 1..3} — the only supported cast path. */
    COMMAND,
    /**
     * Admin debug cast from {@code /bg debug ability <id>}: gateless, cooldown-free, and no
     * {@code TechniqueFx.burst} — mirrors the old {@code AbilityDefinition.perform}-only behavior
     * the debug tools had before routing through the caster.
     */
    DEBUG
}
