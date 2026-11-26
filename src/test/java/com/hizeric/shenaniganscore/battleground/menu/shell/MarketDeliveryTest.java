package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Staged purchases must never require a mutation of the player's live inventory. */
class MarketDeliveryTest {

    @Test
    void stagedPurchasesNeverRequireLiveInventoryMutation() {
        for (BattlegroundPlayer.PurchaseResult result : BattlegroundPlayer.PurchaseResult.values()) {
            assertFalse(MarketDelivery.requiresInventoryMutation(true, true, true, result));
            assertFalse(MarketDelivery.requiresInventoryMutation(true, true, false, result));
            assertFalse(MarketDelivery.requiresInventoryMutation(true, false, false, result));
            assertFalse(MarketDelivery.requiresInventoryMutation(false, false, false, result));
        }
    }
}
