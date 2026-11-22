package com.hizeric.shenaniganscore.battleground.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlegroundPlayerSpendTest {

    @Test
    void spendingZeroIsFreeAndSucceeds() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(0);

        assertTrue(data.spend(0), "a zero-price purchase must not be rejected");
        assertEquals(0, data.credits());
    }

    @Test
    void spendingNegativeIsFreeAndSucceeds() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(10);

        assertTrue(data.spend(-5), "a clamped price must not be rejected");
        assertEquals(10, data.credits(), "a free purchase must not mint credits");
    }

    @Test
    void spendingMoreThanTheBalanceFails() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(3);

        assertFalse(data.spend(4));
        assertEquals(3, data.credits());
    }

    @Test
    void spendingWithinTheBalanceDeducts() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(10);

        assertTrue(data.spend(4));
        assertEquals(6, data.credits());
    }
}
