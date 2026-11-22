package com.hizeric.shenaniganscore.battleground;

/**
 * Decisions at the hub/combat boundary. Kept free of Bukkit types so the exploit-facing branches
 * can be tested without a server.
 */
public final class RoundGates {

    public enum Deploy {
        READY,
        ALREADY_DEPLOYED,
        NO_ARENA,
        RESTORING
    }

    public enum Drops {
        KEEP,
        CLEAR
    }

    public enum Stage {
        STAGE_READY,
        ALREADY_DEPLOYED,
        ALREADY_STAGED,
        NO_ARENA
    }

    private RoundGates() { }

    /**
     * Whether a deploy must run the shared round-start invariants. Only IDLE reaches this branch:
     * the deploy gate admits IDLE/RUNNING/WARNING and blocks STAGING/RESTORING. Kept pure so the
     * "never start a round without a diff ledger" decision is testable without a server.
     */
    public static boolean roundStartRequired(RoundState state) {
        return state != RoundState.RUNNING && state != RoundState.WARNING;
    }

    /**
     * Whether an admin may switch the active map. A tier-1 restore is bound to the map it started
     * on, so switching during STAGING/RESTORING would leave the new map unrestored.
     */
    public static boolean mapSwitchAllowed(RoundState state) {
        return state != RoundState.STAGING && state != RoundState.RESTORING;
    }

    /**
     * Deploy gate keyed on the live {@link RoundState}. RESTORING blocks deploy while the arena
     * is mid-restore; STAGING blocks deploy while tier 1 gate is incomplete (deployment closed,
     * tier 2 may continue in background). Deployment opens when RUNNING.
     */
    public static Deploy deploy(RoundState state, boolean arenaCaptured, boolean alreadyDeployed) {
        if (alreadyDeployed) return Deploy.ALREADY_DEPLOYED;
        if (!arenaCaptured) return Deploy.NO_ARENA;
        if (state == RoundState.RESTORING || state == RoundState.STAGING) return Deploy.RESTORING;
        return Deploy.READY;
    }

    /** `/bg join` stages the player; it must never double as a second deploy path. */
    public static Stage stage(boolean arenaCaptured, boolean deployed, boolean staged) {
        if (deployed) return Stage.ALREADY_DEPLOYED;
        if (!arenaCaptured) return Stage.NO_ARENA;
        if (staged) return Stage.ALREADY_STAGED;
        return Stage.STAGE_READY;
    }

    /** The arena kit is reissued on respawn, so let it drop only outside the mode. */    public static Drops deathDrops(boolean deployed, boolean staged) {
        return deployed || staged ? Drops.CLEAR : Drops.KEEP;
    }

    /**
     * Round-end roster decision. Every deployed player — including one mid-fight or mid-dive — and
     * every staged player is pulled back to the hub so the arena regenerates on an empty map.
     * Players who were never in the mode are left where they are.
     */
    public enum RoundEnd { HUB, NONE }

    public static RoundEnd roundEnd(boolean deployed, boolean diving, boolean staged) {
        return deployed || diving || staged ? RoundEnd.HUB : RoundEnd.NONE;
    }

    /** The hub must not double as an in-combat escape hatch. */
    public static boolean hubAvailable(boolean deployed) {
        return !deployed;
    }

    /** Capturing with players inside bakes their placed blocks and drops into the snapshot. */
    public static boolean captureAllowed(boolean playersInside) {
        return !playersInside;
    }

    public enum RestoreNotice {
        READY,
        FAILED
    }

    public static RestoreNotice restoreNotice(boolean failed) {
        return failed ? RestoreNotice.FAILED : RestoreNotice.READY;
    }
}
