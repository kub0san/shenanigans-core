package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BalanceCommandsTest {

    @Test
    void amountNeverGoesNegative() {
        assertEquals(0, BalanceCommands.normalizeAmount(-50));
        assertEquals(0, BalanceCommands.normalizeAmount(0));
        assertEquals(50, BalanceCommands.normalizeAmount(50));
    }

    @Test
    void creditsOperationsClampAtZero() {
        BattlegroundPlayer data = new BattlegroundPlayer(java.util.UUID.randomUUID());
        BalanceCommands.applyCredits(data, 100, BalanceCommands.Operation.ADD);
        assertEquals(100, data.credits());
        BalanceCommands.applyCredits(data, 250, BalanceCommands.Operation.REMOVE);
        assertEquals(0, data.credits());
        BalanceCommands.applyCredits(data, 42, BalanceCommands.Operation.SET);
        assertEquals(42, data.credits());
    }

    @Test
    void streakOperationsKeepTheBestValueOnlyOnAddAndSet() {
        BattlegroundPlayer data = new BattlegroundPlayer(java.util.UUID.randomUUID());
        BalanceCommands.applyStreak(data, 5, BalanceCommands.Operation.ADD);
        assertEquals(5, data.streak());
        assertEquals(5, data.bestStreak());
        BalanceCommands.applyStreak(data, 50, BalanceCommands.Operation.REMOVE);
        assertEquals(0, data.streak());
        assertEquals(5, data.bestStreak());
        BalanceCommands.applyStreak(data, 9, BalanceCommands.Operation.SET);
        assertEquals(9, data.streak());
        assertEquals(9, data.bestStreak());
    }

    @Test
    void extremeGrantsSaturateInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE, BalanceCommands.saturatedAdd(Integer.MAX_VALUE - 1, 100));
        assertEquals(0, BalanceCommands.saturatedAdd(10, -100));
    }

    @Test
    void onlyTheLiteralAllSelectsEveryone() {
        assertTrue(BalanceCommands.isAll("all"));
        assertTrue(BalanceCommands.isAll("ALL"));
        assertFalse(BalanceCommands.isAll("allan"));
        assertFalse(BalanceCommands.isAll(""));
    }
}
