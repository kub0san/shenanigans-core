package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The per-tick decision for a staggered shrine demolition wave.
 *
 * <p>Dismantle fans its blade waves out over a short stagger so they ripple
 * across the floor rather than firing at once. Each staggered wave runs on its
 * own repeating task that waits out its stagger, fires one {@code Wave}, then
 * cancels itself a moment later.</p>
 *
 * <p>The historical bug: that task only consulted the wall clock — it never
 * asked whether the domain was still alive. If the domain closed during the
 * stagger window (a wave queued behind another), the task still fired its wave
 * into a torn-down world: orphan {@code BlockDisplay} blades and a stray
 * {@code Wave} with no floor to rake. The fix is to gate every tick on session
 * liveness — a dead session cancels the task before it ever launches.</p>
 */
class DismantleWaveGateTest {

    private static final long STAGGER_MS = 200L;

    @Test
    void waitsWhileTheStaggerHasNotElapsed() {
        assertEquals(DismantleWaveGate.Action.WAIT,
                DismantleWaveGate.decide(false, STAGGER_MS - 10, STAGGER_MS, true),
                "before the stagger elapses the task keeps waiting");
    }

    @Test
    void launchesOnceTheStaggerElapsesOnALiveSession() {
        assertEquals(DismantleWaveGate.Action.LAUNCH,
                DismantleWaveGate.decide(false, STAGGER_MS, STAGGER_MS, true),
                "on a live session, the wave launches the moment its stagger elapses");
    }

    @Test
    void cancelsADeadSessionBeforeItEverLaunches() {
        // The bug: the domain closed while this wave was still waiting its turn.
        assertEquals(DismantleWaveGate.Action.CANCEL,
                DismantleWaveGate.decide(false, STAGGER_MS, STAGGER_MS, false),
                "a session that closed mid-stagger must cancel the wave, never launch it");
        assertEquals(DismantleWaveGate.Action.CANCEL,
                DismantleWaveGate.decide(false, 0, STAGGER_MS, false),
                "a dead session cancels even before the stagger would elapse");
    }

    @Test
    void aLaunchedTaskWaitsOutItsTailThenCancels() {
        // Already fired; keep the task alive briefly, then cancel after the tail.
        assertEquals(DismantleWaveGate.Action.WAIT,
                DismantleWaveGate.decide(true, STAGGER_MS + 100, STAGGER_MS, true),
                "after launching, the task idles through its short tail");
        assertEquals(DismantleWaveGate.Action.CANCEL,
                DismantleWaveGate.decide(true, STAGGER_MS + 600, STAGGER_MS, true),
                "once the tail elapses the task cancels itself");
    }

    @Test
    void aDeadSessionCancelsEvenAfterItLaunched() {
        assertEquals(DismantleWaveGate.Action.CANCEL,
                DismantleWaveGate.decide(true, STAGGER_MS + 10, STAGGER_MS, false),
                "a session that dies after launch still cancels the lingering task promptly");
    }
}