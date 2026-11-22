package com.hizeric.shenaniganscore.battleground.arena;

/**
 * Pure decision for "may this player be inside the arena?".
 *
 * <p>The arena is reachable only through the battleground hub, so a player with no
 * mode state has nothing to do in it. Kept free of Bukkit types so the branches can be
 * unit-tested; the listener translates the result into an event cancel.</p>
 */
public final class ArenaEntryDecision {

    public enum Verdict {
        ALLOW,
        DENY
    }

    public record Input(boolean destinationInArena, boolean deployed, boolean staged,
                        boolean admin, boolean internalTransit) { }

    private ArenaEntryDecision() { }

    /**
     * Outside the arena there is nothing to guard. Inside, a player must be deployed,
     * staged, an admin, or riding a one-shot token the plugin itself just issued.
     */
    public static Verdict evaluate(Input input) {
        if (input == null || !input.destinationInArena()) return Verdict.ALLOW;
        if (input.internalTransit()) return Verdict.ALLOW;
        if (input.admin()) return Verdict.ALLOW;
        if (input.deployed() || input.staged()) return Verdict.ALLOW;
        return Verdict.DENY;
    }
}
