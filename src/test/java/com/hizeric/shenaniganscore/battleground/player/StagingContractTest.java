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

        // 1. first deploy captures the arena kit (41 slots).
        inventories.captureOnce(uuid, new ItemStack[41], null);

        // 2. an item is acquired in the hub during the round (42 slots on the next restore).
        ItemStack[] hubInventoryWithNewItem = new ItemStack[42];
        ArenaInventories.Snapshot refreshedHub =
                new ArenaInventories.Snapshot(hubInventoryWithNewItem, null);

        // 3. round boundary: DeployService.restoreInventory() hands the hub kit back, then
        //    RoundController.beginReset() refreshes the snapshot before staging.enter(). The
        //    restore-before-enter ordering itself (RoundController.java:183-186) needs live
        //    managers/Bukkit and is not reproducible in this pure snapshot seam.
        inventories.replace(uuid, refreshedHub);
        assertTrue(inventories.has(uuid), "staging.enter must still find the refreshed snapshot");

        // 4. redelivery on the next deploy must carry the new hub item, not the stale first kit.
        ArenaInventories.Snapshot boundary = inventories.take(uuid);
        assertNotNull(boundary);
        assertEquals(42, boundary.contents().length,
                "the refreshed hub inventory must win over the stale first-deploy snapshot");
    }
}
