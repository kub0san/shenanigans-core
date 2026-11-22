package com.hizeric.shenaniganscore.core.ability;

/** Outcome of an ability cast attempt, independent of the source that requested it. */
public enum CastResult {
    OK,
    UNKNOWN_ABILITY,
    NOT_IN_ARENA,
    NOT_OWNED,
    HELD_ITEM_REQUIRED,
    DOMAIN_LOCKED,
    COOLDOWN;

    public boolean success() {
        return this == OK;
    }
}
