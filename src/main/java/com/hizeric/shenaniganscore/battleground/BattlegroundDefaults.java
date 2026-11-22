package com.hizeric.shenaniganscore.battleground;

/**
 * Every battleground gameplay value, hardcoded. The battleground YAMLs are version stubs, so
 * these are the source of truth — copy verbatim from the published defaults, never retune.
 */
public final class BattlegroundDefaults {

    private BattlegroundDefaults() { }

    // ---- module
    public static final boolean ENABLED = true;
    public static final String RESTORE_ENGINE = "CLIPBOARD";
    /** Budget for the budgeted paste; clamped to 1-50ms per tick. */
    public static final int RESTORE_BUDGET_MS = 18;
    public static final String PROTECTION_MESSAGE = "<red>This arena block is protected.";

    // ---- round
    // Round length and the warning threshold now live on RoundClock (see ticket 07); the clock
    // pauses whenever nobody is deployed — there is no kill-switch.
    public static final boolean RESET_CREDITS = false;

    // ---- economy
    // Flat credits per kill. No assists, bounties, streak credit bonuses or anti-farm rules.
    public static final int KILL_REWARD = 10;

    // ---- combat
    public static final int LAST_DAMAGER_WINDOW_SECONDS = 10;

    // ---- deploy sky-drop (on-demand scored selection across the whole arena)
    /** Grid stride for sampling candidate columns in the arena rectangle. */
    public static final int DEPLOY_SAMPLE_STEP = 3;
    /**
     * Legacy fallback for the lowest Y the ground scan will reach. Live selection uses
     * {@code world.getMinHeight()} instead (C10); this remains documented for parsers/tests.
     */
    public static final int DEPLOY_MIN_Y = -64;
    /** Normal spawn height above a candidate's own surface (the arena midpoint is never used). */
    public static final int DEPLOY_DROP_HEIGHT = 20;
    /** Extra margin above the highest obstruction in the drop column. */
    public static final int DEPLOY_SPAWN_CLEARANCE = 3;
    /** How far above the surface obstructions are scored. */
    public static final int DEPLOY_CLEARANCE_HEIGHT = 10;
    /** Engagement-band centre: new drops are pulled toward other deployed players at this radius. */
    public static final int DEPLOY_SWEET_SPOT = 12;
    /** Width of the engagement band; smaller means a tighter ring around the sweet spot. */
    public static final int DEPLOY_SIGMA = 8;
    /** Hard minimum distance from an anchor — no stacking on top of another player. */
    public static final int DEPLOY_MIN_GAP = 5;
    /** Strictest-first relaxation tiers: flatness, obstruction count and minimum attraction. */
    public static final java.util.List<com.hizeric.shenaniganscore.battleground.deploy.DropZone.Tier>
            DEPLOY_TIERS = java.util.List.of(
                    new com.hizeric.shenaniganscore.battleground.deploy.DropZone.Tier(2, 0, 0.55),
                    new com.hizeric.shenaniganscore.battleground.deploy.DropZone.Tier(4, 2, 0.30),
                    new com.hizeric.shenaniganscore.battleground.deploy.DropZone.Tier(8, 6, 0.05),
                    new com.hizeric.shenaniganscore.battleground.deploy.DropZone.Tier(
                            Integer.MAX_VALUE, Integer.MAX_VALUE, -1e9));
    /** Hard cap on downward velocity; the ramp approaches it over the fall. */
    public static final double DEPLOY_DOWN_SPEED = 2.0;
    /** Safety limit on dive length before it aborts. */
    public static final int DEPLOY_MAX_TICKS = 1200;
    /** Ticks before the landing check arms, so the spawn frame is never read as "on ground". */
    public static final int DEPLOY_LANDING_ARM_TICKS = 5;
    /** Message shown when a diver touches water or lava mid-descent. */
    public static final String DEPLOY_LIQUID_MESSAGE =
            "<red>You hit the water/lava mid-drop — returned to the hub. Try Deploy again.";

    // ---- in-mode command policy (the listener whitelist lives in CommandPolicy)
    public static final boolean COMMAND_POLICY_ENABLED = true;
    public static final com.hizeric.shenaniganscore.battleground.combat.CommandPolicy.Mode COMMAND_POLICY_MODE =
            com.hizeric.shenaniganscore.battleground.combat.CommandPolicy.Mode.WHITELIST;
}
