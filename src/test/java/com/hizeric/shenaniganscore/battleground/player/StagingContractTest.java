package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.GameMode;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** D2/D4: exiting staging must assert known-good values, never replay a poisoned snapshot. */
class StagingContractTest {

    @Test
    void exitStateClearsEveryFlagThatLeaksToPlayerData() {
        StagingContract.ExitState state = StagingContract.exitState();

        assertFalse(state.invulnerable());
        assertFalse(state.invisible());
        assertTrue(state.collidable());
        assertFalse(state.allowFlight());
        assertFalse(state.flying());
        assertEquals(GameMode.SURVIVAL, state.gameMode());
    }

    @Test
    void commandAllowlistStripsTheLeadingSlash() {
        assertTrue(StagingContract.isAllowedCommand("/battleground quit"));
        assertTrue(StagingContract.isAllowedCommand("/bg join"));
        assertTrue(StagingContract.isAllowedCommand("/ability 2"));
        assertFalse(StagingContract.isAllowedCommand("/technique list"));
        assertFalse(StagingContract.isAllowedCommand("/spawn"));
    }

    @Test
    void neverStagedPlayersKeepTheirOwnFlags() {
        assertFalse(StagingContract.shouldExit(false, false));
        assertTrue(StagingContract.shouldExit(true, false));
        assertTrue(StagingContract.shouldExit(false, true));
    }

    @Test
    void hubItemAcquiredAfterFirstDeploySurvivesTheRoundBoundary() {
        // putIfAbsent pins the first-deploy snapshot forever; the round boundary must refresh it
        // with the hub inventory so an item acquired since then is not destroyed at the next exit.
        UUID uuid = UUID.randomUUID();
        ArenaInventories inventories = new ArenaInventories();
        inventories.captureOnce(uuid, new ItemStack[41], null); // first deploy

        ItemStack[] hubInventoryWithNewItem = new ItemStack[42];
        inventories.replace(uuid, new ArenaInventories.Snapshot(hubInventoryWithNewItem, null));

        ArenaInventories.Snapshot boundary = inventories.take(uuid);
        assertNotNull(boundary);
        assertEquals(42, boundary.contents().length,
                "the refreshed hub inventory must win over the stale first-deploy snapshot");
    }
}
