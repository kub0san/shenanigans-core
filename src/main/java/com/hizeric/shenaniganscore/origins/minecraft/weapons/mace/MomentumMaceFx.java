package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;

public final class MomentumMaceFx {
    private MomentumMaceFx() { }
    public static void vault(Player p) { p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 8, 0.2,0.2,0.2,0.05); }
    public static void dive(Player p) { p.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, p.getLocation(), 8, 0.2,0.2,0.2,0.05); }
}