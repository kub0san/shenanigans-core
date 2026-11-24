package com.hizeric.shenaniganscore.core.cooldown;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the cooldown "totals" retention leak (#58 / SP-LEAD-001).
 *
 * <p>The 60-second sweep only pruned the timestamp maps, leaving one stale entry per
 * (player, key) in the totals maps forever. These tests force the timestamps into the
 * past and assert the sweep removes the matching totals too.</p>
 */
class CooldownExpiryPruningTest {

    @Test
    void expiredKeyedCooldownIsPurgedFromTotals() throws Exception {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.tryClaim(player, "blue", 30);
        assertEquals(30, manager.getTotalSeconds(player, "blue"));

        expireKeyed(manager, "blue", player);
        manager.clearExpired();

        assertEquals(0, manager.getTotalSeconds(player, "blue"));
        assertEquals(0, manager.activeKeyCount(), "empty keyed buckets must be dropped too");
    }

    @Test
    void expiredGlobalCooldownIsPurgedFromTotals() throws Exception {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.setCooldown(player, 30);
        assertEquals(30, manager.getTotalSeconds(player, "global"));

        expireGlobal(manager, player);
        manager.clearExpired();

        assertEquals(0, manager.getTotalSeconds(player, "global"));
    }

    @Test
    void clearAllStillRemovesTotals() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.tryClaim(player, "red", 30);
        manager.clearAll(player);
        assertEquals(0, manager.getTotalSeconds(player, "red"));
    }

    @SuppressWarnings("unchecked")
    private static void expireGlobal(CooldownManager manager, UUID player) throws Exception {
        Map<UUID, Long> map = field(manager, "cooldowns");
        map.put(player, System.currentTimeMillis() - 1_000L);
    }

    @SuppressWarnings("unchecked")
    private static void expireKeyed(CooldownManager manager, String key, UUID player) throws Exception {
        Map<String, Map<UUID, Long>> map = field(manager, "keyedCooldowns");
        Map<UUID, Long> bucket = map.get(key);
        assertTrue(bucket != null && bucket.containsKey(player));
        bucket.put(player, System.currentTimeMillis() - 1_000L);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(CooldownManager manager, String name) throws Exception {
        Field f = CooldownManager.class.getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(manager);
    }
}
