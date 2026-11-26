package com.hizeric.shenaniganscore.battleground.arena;

/**
 * Pure decision for "may this player be inside the arena?".
 *
 * <p>The arena is reachable only through the battleground hub, so a player with no
 * mode state has nothing to do in it. Kept free of Bukkit types so the branches can be
 * unit-tested; the listener translates the result into an event cancel.</p>
 *
 * <p>Issue #64 distinguishes two cases that used to collapse into one. <em>Ingress</em> (the
 * destination is inside but the origin is not) is {@link Verdict#DENY} for everyone without a
 * mode flag. A player who is <em>already inside</em> without a mode flag is {@link Verdict#EJECT}:
 * the exploit should send them to the return point, not let them walk out unscathed. Deployed,
 * staged, admin and one-shot-token players stay {@link Verdict#ALLOW} in both directions so the
 * normal deploy/exit path is untouched.</p>
 */
public final class ArenaEntryDecision {

    public enum Verdict {
        ALLOW,
        DENY,
        EJECT
    }

    public record Input(boolean destinationInArena, boolean originInArena, boolean deployed,
                        boolean staged, boolean admin, boolean internalTransit) {
        /** Ingress-only convenience constructor: callers that have no origin flag (teleport and
         * respawn guards) keep the original "destination is guarded" semantics. */
        public Input(boolean destinationInArena, boolean deployed, boolean staged,
                     boolean admin, boolean internalTransit) {
            this(destinationInArena, false, deployed, staged, admin, internalTransit);
        }
    }

    private ArenaEntryDecision() { }

    /** Whether two block coordinates are the same in all three dimensions. */
    public static boolean sameBlock(int ax, int ay, int az, int bx, int by, int bz) {
        return ax == bx && ay == by && az == bz;
    }

    /**
     * Fail open when neither side is guarded (or the input is null). Authorised players may be
     * anywhere; an unauthorised origin inside the guarded volume is ejected, and an unauthorised
     * destination inside is denied.
     */
    public static Verdict evaluate(Input input) {
        if (input == null) return Verdict.ALLOW;
        boolean origin = input.originInArena();
        boolean destination = input.destinationInArena();
        if (!origin && !destination) return Verdict.ALLOW;

        boolean authorised = input.internalTransit() || input.admin()
                || input.deployed() || input.staged();
        if (authorised) return Verdict.ALLOW;
        if (origin) return Verdict.EJECT;
        return Verdict.DENY;
    }
}
