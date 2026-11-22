package com.hizeric.shenaniganscore.battleground.deploy;

/**
 * Turns a drop search result into a UI decision. A failed search never ends the join as a full hub
 * exit: the player stays staged and is told a specific, actionable reason so the next Deploy click
 * can retry. Kept free of Bukkit types so that policy is unit-testable.
 */
public final class DeployFeedback {

    public enum Outcome { DROP, RESTAGE }

    public record Result(Outcome outcome, String message) { }

    private DeployFeedback() { }

    public static Result forSelection(DropZone.Selection selection) {
        if (selection == null || selection.found()) return new Result(Outcome.DROP, null);
        return new Result(Outcome.RESTAGE, message(selection.reason()));
    }

    /** A specific MiniMessage explanation, one per failure reason. */
    public static String message(DropZone.Reason reason) {
        if (reason == null) return generic();
        return switch (reason) {
            case NONE -> generic();
            case NO_SOLID_GROUND -> "<red>No solid landing ground was found in the arena. "
                    + "Stay in the hub and try Deploy again shortly.";
            case ALL_LIQUID -> "<red>The arena has no dry landing spot — every column is in water "
                    + "or lava. Stay in the hub and try Deploy again shortly.";
            case OBSTRUCTED -> "<red>The arena is too rough or cliffed to drop safely right now. "
                    + "Stay in the hub and try Deploy again shortly.";
        };
    }

    private static String generic() {
        return "<red>No safe drop location was available. Stay in the hub and try Deploy again.";
    }
}
