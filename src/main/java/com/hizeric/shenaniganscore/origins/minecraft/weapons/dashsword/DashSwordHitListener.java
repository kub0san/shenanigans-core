package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.cooldown.CooldownManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Refunds one second of dash cooldown for every landed Dash Sword hit.
 *
 * <p>Only deployed fighters count, so hitting passive mobs outside the arena cannot farm the
 * refund. The manager is looked up at event time because abilities are registered before the
 * battleground manager is constructed.</p>
 */
public final class DashSwordHitListener implements Listener {

    private static final int REFUND_SECONDS = 1;

    private final Supplier<CooldownManager> cooldowns;
    private final Predicate<UUID> deployed;

    public DashSwordHitListener(ShenanigansCore plugin) {
        this(() -> plugin.getCooldownManager(), uuid -> plugin.getBattlegroundManager() != null
                && plugin.getBattlegroundManager().isDeployed(uuid));
    }

    DashSwordHitListener(Supplier<CooldownManager> cooldowns, Predicate<UUID> deployed) {
        this.cooldowns = cooldowns;
        this.deployed = deployed;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        UUID uuid = attacker.getUniqueId();
        if (!deployed.test(uuid)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!DashSwordItem.isDashSword(weapon)) return;

        cooldowns.get().reduceCooldown(uuid, DashSwordAbility.ID.toString(), REFUND_SECONDS);
    }
}
