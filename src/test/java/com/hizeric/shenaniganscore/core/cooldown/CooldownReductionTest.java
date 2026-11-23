package com.hizeric.shenaniganscore.core.cooldown;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-Java coverage for the neutral cooldown-shortening mechanism. */
class CooldownReductionTest {

    @Test
    void oneSecondReductionShowsUpInRemainingTime() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.setCooldown(player, "minecraft:dash_sword", 30);

        assertTrue(manager.reduceCooldown(player, "minecraft:dash_sword", 1));

        assertEquals(29, manager.getRemainingSeconds(player, "minecraft:dash_sword"));
    }

    @Test
    void reductionClampsAtZeroInsteadOfGoingNegative() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.setCooldown(player, "minecraft:dash_sword", 1);

        assertTrue(manager.reduceCooldown(player, "minecraft:dash_sword", 10));

        assertEquals(0, manager.getRemainingSeconds(player, "minecraft:dash_sword"));
        assertFalse(manager.isOnCooldown(player, "minecraft:dash_sword"));
    }

    @Test
    void otherKeysAreUntouched() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.setCooldown(player, "minecraft:dash_sword", 30);
        manager.setCooldown(player, "jjk:blue", 30);

        manager.reduceCooldown(player, "minecraft:dash_sword", 1);

        assertEquals(29, manager.getRemainingSeconds(player, "minecraft:dash_sword"));
        assertEquals(30, manager.getRemainingSeconds(player, "jjk:blue"));
    }

    @Test
    void totalStaysAtTheOriginalDurationForGauges() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();
        manager.setCooldown(player, "minecraft:dash_sword", 30);

        manager.reduceCooldown(player, "minecraft:dash_sword", 10);

        assertEquals(30, manager.getTotalSeconds(player, "minecraft:dash_sword"));
        assertEquals(20, manager.getRemainingSeconds(player, "minecraft:dash_sword"));
    }

    @Test
    void anInactiveCooldownIsNotRevivedOrShortened() {
        CooldownManager manager = new CooldownManager();
        UUID player = UUID.randomUUID();

        assertFalse(manager.reduceCooldown(player, "minecraft:dash_sword", 1));
        assertEquals(0, manager.getRemainingSeconds(player, "minecraft:dash_sword"));
    }
}
