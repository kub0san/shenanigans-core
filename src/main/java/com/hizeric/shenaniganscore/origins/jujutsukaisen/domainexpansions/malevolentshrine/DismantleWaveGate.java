package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

/**
 * Pure per-tick decision for one staggered shrine demolition wave.
 *
 * <p>Each staggered wave runs on its own short repeating task: it waits out its
 * stagger, fires a single {@code Wave}, then cancels itself a moment later. The
 * decision of what to do on a given tick is kept here — free of Bukkit — so it
 * can be unit-tested ({@code DismantleWaveGateTest}). The crucial rule is the
 * liveness gate: a session that has closed cancels the task before it ever
 * launches, so no orphan wave fires into a torn-down domain.</p>
 */
final class DismantleWaveGate {

    /** What a staggered-wave task should do this tick. */
    enum Action {
        /** Do nothing; check again next tick. */
        WAIT,
        /** Fire the wave now and mark the task as launched. */
        LAUNCH,
        /** Stop the task; it has nothing left to do. */
        CANCEL
    }

    /** How long the task lingers after launching before it cancels itself (ms). */
    static final long TAIL_MS = 500L;

    private DismantleWaveGate() {
    }

    /**
     * @param launched       whether this task has already fired its wave
     * @param now            current wall-clock time (ms)
     * @param start          wall-clock time the stagger elapses (ms)
     * @param sessionActive  whether the owning domain session is still alive
     */
    static Action decide(boolean launched, long now, long start, boolean sessionActive) {
        if (!sessionActive) return Action.CANCEL;
        boolean staggerElapsed = now >= start;
        if (!launched && staggerElapsed) return Action.LAUNCH;
        if (now - start > TAIL_MS) return Action.CANCEL;
        return Action.WAIT;
    }
}