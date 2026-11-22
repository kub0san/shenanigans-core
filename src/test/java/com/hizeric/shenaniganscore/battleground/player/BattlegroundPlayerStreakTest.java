package com.hizeric.shenaniganscore.battleground.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Highest streak is a permanent display stat; the current streak is still rented. */
class BattlegroundPlayerStreakTest {

    private static BattlegroundPlayer player() {
        return new BattlegroundPlayer(UUID.randomUUID());
    }

    @Test
    void aKillRaisesBothCurrentAndHighestStreak() {
        BattlegroundPlayer player = player();
        player.recordKill();
        player.recordKill();
        player.recordKill();
        assertEquals(3, player.streak());
        assertEquals(3, player.bestStreak());
    }

    @Test
    void deathAndRoundResetClearOnlyTheCurrentStreak() {
        BattlegroundPlayer player = player();
        player.recordKill();
        player.recordKill();
        player.recordDeath();
        assertEquals(0, player.streak());
        assertEquals(2, player.bestStreak());

        player.recordKill();
        player.resetRound();
        assertEquals(0, player.streak());
        assertEquals(2, player.bestStreak());
    }

    @Test
    void migratedValueNeverLowersTheRecord() {
        BattlegroundPlayer player = player();
        player.recordKill();
        player.recordKill();
        player.recordBestStreak(9);
        assertEquals(9, player.bestStreak());
        player.recordBestStreak(1);
        assertEquals(9, player.bestStreak());
    }
}
