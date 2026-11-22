package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C1: the arena kit must never overwrite a player's real inventory, and leaving the mode must
 * hand back exactly what was taken — once. Arrays of nulls are enough to exercise the store
 * without constructing Bukkit ItemStacks (whose static initializer needs a running server).
 */
class ArenaInventoriesTest {

    private static ItemStack[] slots(int size) {
        return new ItemStack[size];
    }

    @Test
    void firstDeployIsTheOnlyCapture() {
        UUID uuid = UUID.randomUUID();
        ArenaInventories inventories = new ArenaInventories();
        ItemStack[] original = slots(41);

        inventories.captureOnce(uuid, original, null);
        inventories.captureOnce(uuid, slots(2), null);

        ItemStack[] kept = inventories.take(uuid).contents();
        assertEquals(41, kept.length, "a later deploy must not replace the original snapshot");
    }

    @Test
    void takeReturnsTheSnapshotExactlyOnce() {
        UUID uuid = UUID.randomUUID();
        ArenaInventories inventories = new ArenaInventories();
        inventories.captureOnce(uuid, slots(41), null);

        assertNotNull(inventories.take(uuid));
        assertNull(inventories.take(uuid), "a second quit must not restore the kit again");
        assertFalse(inventories.has(uuid));
    }

    @Test
    void snapshotCopiesTheCallerArray() {
        UUID uuid = UUID.randomUUID();
        ArenaInventories inventories = new ArenaInventories();
        ItemStack[] source = slots(2);

        inventories.captureOnce(uuid, source, null);
        source[0] = null;

        assertArrayEquals(slots(2), inventories.take(uuid).contents());
    }

    @Test
    void forgetAndClearDropEverySnapshot() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        ArenaInventories inventories = new ArenaInventories();
        inventories.captureOnce(a, slots(1), null);
        inventories.captureOnce(b, slots(1), null);

        inventories.forget(a);
        assertNull(inventories.take(a));
        assertNotNull(inventories.take(b));

        inventories.captureOnce(a, slots(1), null);
        inventories.clear();
        assertFalse(inventories.has(a));
        assertTrue(inventories.snapshotCount() == 0);
    }
}
