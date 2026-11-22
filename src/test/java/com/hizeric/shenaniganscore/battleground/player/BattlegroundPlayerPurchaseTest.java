package com.hizeric.shenaniganscore.battleground.player;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The player economy seam: catalog purchases are permanent, streak is not. */
class BattlegroundPlayerPurchaseTest {

    @Test
    void purchaseDeductsCreditsAndGrantsPermanentOwnership() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(50);
        data.setStreak(4);

        assertEquals(BattlegroundPlayer.PurchaseResult.SUCCESS, data.purchase("dash", 10));
        assertEquals(40, data.credits());
        assertTrue(data.owns("dash"));

        data.recordKill();
        data.recordDeath();
        data.resetRound();

        assertTrue(data.owns("dash"), "a purchase must survive death and round reset");
        assertEquals(40, data.credits(), "a purchase must not be refunded or wiped");
        assertEquals(0, data.streak(), "the streak is the per-round gate and must reset");
    }

    @Test
    void insufficientCreditsAreRejectedWithAReason() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(3);

        assertEquals(BattlegroundPlayer.PurchaseResult.INSUFFICIENT_CREDITS, data.purchase("blue", 10));
        assertEquals(3, data.credits());
        assertFalse(data.owns("blue"));
    }

    @Test
    void duplicatePurchaseDoesNotChargeAgain() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(50);

        assertEquals(BattlegroundPlayer.PurchaseResult.SUCCESS, data.purchase("blue", 10));
        assertEquals(BattlegroundPlayer.PurchaseResult.ALREADY_OWNED, data.purchase("blue", 10));
        assertEquals(40, data.credits());
    }

    @Test
    void revokingOwnershipAfterAFailedDeliveryRestoresThePurchaseState() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(50);
        assertEquals(BattlegroundPlayer.PurchaseResult.SUCCESS, data.purchase("dash", 10));
        // Market delivery failure refunds the charge; ownership must be rolled back with it,
        // otherwise the Loadout Items tab can materialize the item for free.
        data.revokeEntry("dash");
        data.addCredits(10);
        assertFalse(data.owns("dash"));
        assertEquals(50, data.credits());
    }
    @Test
    void freePurchaseStillRecordsOwnership() {
        BattlegroundPlayer data = new BattlegroundPlayer(UUID.randomUUID());
        data.setCredits(0);

        assertEquals(BattlegroundPlayer.PurchaseResult.SUCCESS, data.purchase("starter", 0));
        assertTrue(data.owns("starter"));
        assertEquals(0, data.credits());
    }
}
