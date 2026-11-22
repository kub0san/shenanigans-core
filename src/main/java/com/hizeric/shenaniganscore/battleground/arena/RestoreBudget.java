package com.hizeric.shenaniganscore.battleground.arena;

/** Keeps a restore inside a per-tick time slice. The manager owns one instance per restore. */
public final class RestoreBudget {

    private final long maxNanos;
    private long tickStart;

    public RestoreBudget(int maxMillisPerTick) {
        this.maxNanos = Math.max(1, maxMillisPerTick) * 1_000_000L;
    }

    /** Call at the start of each tick before {@code canContinue()}. */
    public void beginTick() {
        tickStart = System.nanoTime();
    }

    public boolean canContinue() {
        return System.nanoTime() - tickStart < maxNanos;
    }

    public long elapsedMillis() {
        return (System.nanoTime() - tickStart) / 1_000_000L;
    }
}
