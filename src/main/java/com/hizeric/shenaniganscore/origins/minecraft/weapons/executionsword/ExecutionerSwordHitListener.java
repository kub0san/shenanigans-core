package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Arms the next landed Executioner's Sword hit and dispatches the variant (uppercut vs
 * execution) based on the target's health at contact.
 *
 * <p>Unlike {@code DashSwordHitListener}, this listener does NOT refund cooldowns; the
 * cooldown is claimed atomically by {@code AbilityCaster.cast} when the ability is cast.</p>
 */
public final class ExecutionerSwordHitListener implements Listener {

    public static final long ARM_DURATION_MS = ExecutionerSwordItem.ARM_DURATION_MS;

    private final Map<UUID, Long> armedUntil = new ConcurrentHashMap<>();
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public ExecutionerSwordHitListener() {
    }

    public void arm(Player player) {
        armedUntil.put(player.getUniqueId(), System.currentTimeMillis() + ARM_DURATION_MS);
        SoundUtils.play(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        // Arming is otherwise invisible: without this, both /ability 1 and /bg debug ability
        // look like no-ops until the player happens to land a hit.
        player.sendMessage(MM.deserialize(
                "<dark_red><bold>EXECUTIONER'S SWORD ARMED <gray>- strike within 6s"));
    }

    public boolean isArmed(Player player) {
        Long expiry = armedUntil.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() >= expiry) {
            armedUntil.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!ExecutionerSwordItem.isExecutionerSword(weapon)) return;
        if (!isArmed(attacker)) return;

        Entity target = event.getEntity();
        if (!(target instanceof LivingEntity living)) return;

        armedUntil.remove(attacker.getUniqueId());
        event.setCancelled(true);
        if (target instanceof Player pTarget) {
            if (pTarget.getHealth() <= ExecutionerSwordItem.EXECUTE_THRESHOLD) {
                ExecutionerSwordEffect.execute(attacker, pTarget);
            } else {
                ExecutionerSwordEffect.uppercut(attacker, pTarget);
            }
        } else {
            ExecutionerSwordEffect.uppercut(attacker, living);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        armedUntil.remove(event.getPlayer().getUniqueId());
    }
}
