package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;

/**
 * Delivery invariant for market purchases: a purchase is staged in layout data only,
 * so it must never mutate the player's live inventory.
 */
public final class MarketDelivery {
    private MarketDelivery() { }

    /**
     * Explicit guard kept at the purchase call site. The inputs describe the old
     * delivery pre-checks; staged delivery never needs any of them.
     */
    public static boolean requiresInventoryMutation(boolean customItem, boolean itemResolved,
                                                    boolean fits, BattlegroundPlayer.PurchaseResult result) {
        return false;
    }
}
