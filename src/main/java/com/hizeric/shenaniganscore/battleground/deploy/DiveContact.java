package com.hizeric.shenaniganscore.battleground.deploy;

/**
 * Pure classification of what a falling player's body is touching. Liquid contact is reported ahead
 * of ground contact so a player who falls into the arena pool is re-staged with an explanation
 * instead of being silently treated as a safe touchdown.
 */
public final class DiveContact {

    public enum Kind { NONE, GROUND, LIQUID }

    private DiveContact() { }

    public static Kind classify(boolean onGround, boolean feetLiquid, boolean bodyLiquid) {
        if (feetLiquid || bodyLiquid) return Kind.LIQUID;
        if (onGround) return Kind.GROUND;
        return Kind.NONE;
    }
}
