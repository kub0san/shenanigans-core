package com.hizeric.shenaniganscore.battleground.ability;

/**
 * Cast eligibility for a command slot. Sneak, hotbar, keybind and item inputs are gone:
 * the only cast path is {@code /ability 1..3}.
 */
public final class CastInput {

    public enum Decision {
        CAST,
        NOT_READY,
        IGNORE
    }

    public enum Reason {
        OK,
        BAD_SLOT,
        NOT_DEPLOYED,
        NOT_OWNED,
        ON_COOLDOWN,
        STREAK_NOT_MET,
        HELD_ITEM_MISMATCH,
        ULTIMATE_NOT_BOUND
    }

    public record Result(Decision decision, Reason reason, int cooldownSeconds) { }

    private CastInput() {
    }

    /** Legacy five-argument form; assumes the held item and ultimate gates are satisfied. */
    public static Decision decide(boolean deployed, boolean owned, boolean onCooldown,
                                  boolean streakMet, int slot) {
        return decide(deployed, owned, onCooldown, streakMet, slot, true, true, 0).decision();
    }

    public static Result decide(boolean deployed, boolean owned, boolean onCooldown,
                                boolean streakMet, int slot, boolean heldItemMatches,
                                boolean ultimateBound, int cooldownSeconds) {
        if (slot < 0 || slot >= AbilityRail.TOTAL) {
            return new Result(Decision.IGNORE, Reason.BAD_SLOT, 0);
        }
        if (!deployed) return new Result(Decision.IGNORE, Reason.NOT_DEPLOYED, 0);
        if (!owned) return new Result(Decision.IGNORE, Reason.NOT_OWNED, 0);
        if (slot == AbilityRail.HELD_SLOT && !heldItemMatches) {
            return new Result(Decision.IGNORE, Reason.HELD_ITEM_MISMATCH, 0);
        }
        if (slot == AbilityRail.ULTIMATE_SLOT && !ultimateBound) {
            return new Result(Decision.IGNORE, Reason.ULTIMATE_NOT_BOUND, 0);
        }
        if (onCooldown) {
            return new Result(Decision.NOT_READY, Reason.ON_COOLDOWN, Math.max(0, cooldownSeconds));
        }
        if (!streakMet) return new Result(Decision.NOT_READY, Reason.STREAK_NOT_MET, 0);
        return new Result(Decision.CAST, Reason.OK, 0);
    }
}
