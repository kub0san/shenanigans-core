package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

/** Lightweight, non-mechanical feedback. Works without the resource pack. */
public final class MomentumMaceFx {
    private MomentumMaceFx() { }

    public static void vault(Player p) {
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 12, 0.3, 0.3, 0.3, 0.05);
        p.getWorld().playSound(p, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 1.2f);
    }

    public static void dive(Player p) {
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation(), 10, 0.2, 0.2, 0.2, 0.03);
        p.getWorld().playSound(p, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
    }

    public static void impact(Player p) {
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 16, 0.4, 0.1, 0.4, 0.08);
        p.getWorld().playSound(p, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.1f);
    }
}
