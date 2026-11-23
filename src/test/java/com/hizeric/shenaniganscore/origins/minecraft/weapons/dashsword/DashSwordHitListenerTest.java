package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.battleground.item.BattlegroundItemKeys;
import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The hit-refund policy: sword, deployed attacker, and exactly one second. */
class DashSwordHitListenerTest {

    @AfterEach
    void unmockBukkit() {
        MockBukkit.unmock();
    }

    private static ItemStack stampedSword() {
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = sword.getItemMeta();
        BattlegroundItemKeys.stamp(meta, DashSwordItem.ID);
        sword.setItemMeta(meta);
        return sword;
    }

    private static EntityDamageByEntityEvent hit(PlayerMock attacker, PlayerMock victim) {
        return new EntityDamageByEntityEvent(attacker, victim,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
    }

    @Test
    void aDashSwordHitShavesExactlyOneSecond() {
        ServerMock server = MockBukkit.mock();
        CooldownManager cooldowns = new CooldownManager();
        PlayerMock attacker = server.addPlayer();
        String key = DashSwordAbility.ID.toString();
        cooldowns.setCooldown(attacker.getUniqueId(), key, 30);
        attacker.getInventory().setItemInMainHand(stampedSword());

        new DashSwordHitListener(() -> cooldowns, ignored -> true)
                .onHit(hit(attacker, server.addPlayer()));

        assertEquals(29, cooldowns.getRemainingSeconds(attacker.getUniqueId(), key));
    }

    @Test
    void aPlainSwordDoesNotRefund() {
        ServerMock server = MockBukkit.mock();
        CooldownManager cooldowns = new CooldownManager();
        PlayerMock attacker = server.addPlayer();
        String key = DashSwordAbility.ID.toString();
        cooldowns.setCooldown(attacker.getUniqueId(), key, 30);
        attacker.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        new DashSwordHitListener(() -> cooldowns, ignored -> true)
                .onHit(hit(attacker, server.addPlayer()));

        assertEquals(30, cooldowns.getRemainingSeconds(attacker.getUniqueId(), key));
    }

    @Test
    void anUndeployedAttackerDoesNotRefund() {
        ServerMock server = MockBukkit.mock();
        CooldownManager cooldowns = new CooldownManager();
        PlayerMock attacker = server.addPlayer();
        String key = DashSwordAbility.ID.toString();
        cooldowns.setCooldown(attacker.getUniqueId(), key, 30);
        attacker.getInventory().setItemInMainHand(stampedSword());

        new DashSwordHitListener(() -> cooldowns, uuid -> false)
                .onHit(hit(attacker, server.addPlayer()));

        assertEquals(30, cooldowns.getRemainingSeconds(attacker.getUniqueId(), key));
    }
}
