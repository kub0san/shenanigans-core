package com.hizeric.shenaniganscore.battleground;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round clock: duration captured at construction/reset from live config; warning and
 * pause-when-empty read live each tick. Pure decision seam — no Bukkit, no scheduler.
 */
class RoundClockTest {

    @AfterEach
    void resetConfig() {
        ArenaRuntimeConfig.load(null, Logger.getLogger("RoundClockTest"));
    }

    @Test
    void defaultRoundIsTwentyMinutes() {
        RoundClock clock = new RoundClock();
        assertEquals(1200, clock.roundSeconds());
        assertEquals(1200, clock.secondsRemaining());
    }

    @Test
    void clockCountsDownWhileAnyoneIsDeployed() {
        RoundClock clock = new RoundClock();
        clock.tick(1);
        assertEquals(1199, clock.secondsRemaining());
        clock.tick(3);
        assertEquals(1198, clock.secondsRemaining());
    }

    @Test
    void clockPausesWhenNobodyIsDeployedByDefault() {
        RoundClock clock = new RoundClock();
        clock.tick(0);
        assertEquals(1200, clock.secondsRemaining());
        clock.tick(0);
        assertEquals(1200, clock.secondsRemaining());
        clock.tick(1);
        assertEquals(1199, clock.secondsRemaining());
    }

    @Test
    void pauseWhenEmptyCanBeDisabledLive() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    pause-when-empty: false\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        clock.tick(0);
        assertEquals(1199, clock.secondsRemaining());
        assertTrue(clock.isCounting());
    }

    @Test
    void warningPhaseUsesLiveThreshold() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    duration-seconds: 60\n    warning-seconds: 5\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        assertEquals(60, clock.roundSeconds());
        for (int i = 0; i < 54; i++) clock.tick(1);
        assertEquals(6, clock.secondsRemaining());
        assertEquals(RoundClock.Phase.RUNNING, clock.phase());
        assertEquals(RoundClock.Phase.WARNING, clock.tick(1));
        assertEquals(5, clock.secondsRemaining());
    }

    @Test
    void roundEndsAtZero() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    duration-seconds: 60\n    warning-seconds: 5\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        for (int i = 0; i < 60; i++) clock.tick(1);
        assertEquals(0, clock.secondsRemaining());
        assertEquals(RoundClock.Phase.ENDED, clock.phase());
        clock.tick(1);
        assertEquals(0, clock.secondsRemaining());
    }

    @Test
    void endedClockNeverResumesCounting() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    duration-seconds: 60\n    warning-seconds: 5\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        for (int i = 0; i < 60; i++) clock.tick(1);
        assertEquals(RoundClock.Phase.ENDED, clock.tick(1));
        assertFalse(clock.isCounting());
    }

    @Test
    void pauseDoesNotLeakIntoPhase() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    duration-seconds: 60\n    warning-seconds: 5\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        clock.tick(1);
        assertTrue(clock.isCounting());
        assertEquals(RoundClock.Phase.RUNNING, clock.tick(0));
        assertFalse(clock.isCounting());
    }

    @Test
    void resetCapturesLiveDuration() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    duration-seconds: 90\n    warning-seconds: 5\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        for (int i = 0; i < 90; i++) clock.tick(1);
        assertEquals(RoundClock.Phase.ENDED, clock.phase());
        clock.reset();
        assertEquals(90, clock.roundSeconds());
        assertEquals(90, clock.secondsRemaining());
        assertEquals(RoundClock.Phase.RUNNING, clock.phase());
    }

    @Test
    void setRemainingClampsToCapturedDurationAndUpdatesPhase() throws IOException {
        Path temp = Files.createTempFile("arena-runtime", ".yml");
        Files.writeString(temp, "arena:\n  round:\n    duration-seconds: 120\n    warning-seconds: 10\n");
        ArenaRuntimeConfig.load(temp.toFile(), Logger.getLogger("RoundClockTest"));

        RoundClock clock = new RoundClock();
        clock.setRemaining(5);
        assertEquals(5, clock.secondsRemaining());
        assertEquals(RoundClock.Phase.WARNING, clock.phase());

        clock.setRemaining(9999);
        assertEquals(120, clock.secondsRemaining());
        assertEquals(RoundClock.Phase.RUNNING, clock.phase());

        clock.setRemaining(-5);
        assertEquals(0, clock.secondsRemaining());
        assertEquals(RoundClock.Phase.ENDED, clock.phase());
    }
}