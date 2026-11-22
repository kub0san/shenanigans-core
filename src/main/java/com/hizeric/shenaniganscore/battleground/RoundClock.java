package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;

/**
 * The round clock. Duration is captured at construction/reset from live arena config; warning and
 * pause-when-empty are read live each tick.
 *
 * <p>Pure decision seam. The scheduler lives in {@link BattlegroundManager}; this class owns the
 * arithmetic so round length, pause rule, warning threshold and end transition can be tested
 * without a server.</p>
 */
public final class RoundClock {

    public enum Phase { RUNNING, WARNING, ENDED }

    private volatile int roundSeconds;
    private volatile int secondsRemaining;
    private volatile Phase phase = Phase.RUNNING;
    private volatile boolean counting;

    public RoundClock() {
        reset();
    }

    public int secondsRemaining() { return secondsRemaining; }
    public Phase phase() { return phase; }

    /** @return true when the last {@link #tick(int)} advanced the clock. */
    public boolean isCounting() { return counting; }

    /**
     * Advance one second. Warning and pause-when-empty read live config; duration is the value
     * captured at construction/reset.
     *
     * @param deployed how many players are currently in the arena.
     * @return the phase after this tick.
     */
    public Phase tick(int deployed) {
        if (phase == Phase.ENDED) {
            counting = false;
            return phase;
        }
        boolean pauseWhenEmpty = ArenaRuntimeConfig.current().roundPauseWhenEmpty();
        if (pauseWhenEmpty && deployed <= 0) {
            counting = false;
            return phase;
        }
        counting = true;
        secondsRemaining = Math.max(0, secondsRemaining - 1);
        int warningSeconds = ArenaRuntimeConfig.current().roundWarningSeconds();
        if (secondsRemaining <= 0) {
            phase = Phase.ENDED;
        } else if (secondsRemaining <= warningSeconds) {
            phase = Phase.WARNING;
        } else {
            phase = Phase.RUNNING;
        }
        return phase;
    }

    /** Admin force-start / next-round reset. Captures the live round duration. */
    public void reset() {
        roundSeconds = ArenaRuntimeConfig.current().roundDurationSeconds();
        secondsRemaining = roundSeconds;
        phase = Phase.RUNNING;
        counting = false;
    }

    /** Admin "set remaining time". Clamps to [0, round duration captured at reset]. */
    public void setRemaining(int seconds) {
        secondsRemaining = Math.max(0, Math.min(roundSeconds, seconds));
        int warningSeconds = ArenaRuntimeConfig.current().roundWarningSeconds();
        if (secondsRemaining <= 0) {
            phase = Phase.ENDED;
        } else if (secondsRemaining <= warningSeconds) {
            phase = Phase.WARNING;
        } else {
            phase = Phase.RUNNING;
        }
    }

    /** Duration captured at construction/reset from live config. */
    public int roundSeconds() {
        return roundSeconds;
    }
}