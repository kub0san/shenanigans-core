package com.hizeric.shenaniganscore.battleground.admin;

import com.hizeric.shenaniganscore.battleground.RoundState;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;

/**
 * Admin round-control decisions. Pure seam: no Bukkit, no GUI.
 */
public final class AdminRoundGate {
    private AdminRoundGate() {
    }

    public enum Action { START, PAUSE, RESET, STOP, END, SKIP, SET_TIME }

    public enum Effect { NONE, START, STOP, END, SET_TIME }

    public record Result(boolean allowed, Effect effect, int seconds, String message) {
        public static Result deny(String message) {
            return new Result(false, Effect.NONE, 0, message);
        }

        public static Result allow(Effect effect, int seconds, String message) {
            return new Result(true, effect, seconds, message);
        }
    }

    public static Result decide(Action action, RoundState state, boolean arenaCaptured, int seconds) {
        return switch (action) {
            case START -> decideStart(state, arenaCaptured);
            case PAUSE, STOP -> decideStop(state);
            case RESET, END -> decideEnd(state);
            case SKIP -> decideSkip(state);
            case SET_TIME -> decideSetTime(state, seconds);
        };
    }

    public static boolean mayForceStart(RoundState state, boolean arenaCaptured, boolean restoreInFlight) {
        return arenaCaptured && !restoreInFlight && state == RoundState.IDLE;
    }

    private static Result decideStart(RoundState state, boolean arenaCaptured) {
        if (!arenaCaptured) return Result.deny("Capture an arena before starting a round.");
        if (state == RoundState.RESTORING || state == RoundState.STAGING) {
            return Result.deny("A round cannot start while the arena is resetting.");
        }
        if (state == RoundState.RUNNING || state == RoundState.WARNING) {
            return Result.deny("A round is already live.");
        }
        int duration = ArenaRuntimeConfig.current().roundDurationSeconds();
        if (duration <= 0) duration = ArenaRuntimeConfig.defaults().roundDurationSeconds();
        return Result.allow(Effect.START, duration, "Round started.");
    }

    private static Result decideStop(RoundState state) {
        if (state != RoundState.RUNNING && state != RoundState.WARNING) {
            return Result.deny("No live round to stop.");
        }
        return Result.allow(Effect.STOP, 0, "Round stopped.");
    }

    private static Result decideEnd(RoundState state) {
        if (state != RoundState.RUNNING && state != RoundState.WARNING) {
            return Result.deny("No live round to end.");
        }
        return Result.allow(Effect.END, 0, "Round ended.");
    }

    private static Result decideSkip(RoundState state) {
        if (state != RoundState.RUNNING && state != RoundState.WARNING) {
            return Result.deny("No live round to skip.");
        }
        return Result.allow(Effect.SET_TIME, 0, "Round skipped to end.");
    }

    private static Result decideSetTime(RoundState state, int seconds) {
        if (state != RoundState.RUNNING && state != RoundState.WARNING) {
            return Result.deny("Time can only be set during a live round.");
        }
        return Result.allow(Effect.SET_TIME, Math.max(0, seconds), "Round time updated.");
    }
}
