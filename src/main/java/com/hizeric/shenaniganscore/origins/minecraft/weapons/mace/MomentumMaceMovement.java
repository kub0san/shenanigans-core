package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public final class MomentumMaceMovement {
    private MomentumMaceMovement() { }

    public static void vault(Player player) {
        Vector v = player.getVelocity();
        double retain = 0.70;
        double fx = v.getX() * retain;
        double fz = v.getZ() * retain;
        Vector dir = player.getLocation().getDirection();
        double hx = dir.getX(), hz = dir.getZ();
        double len = Math.hypot(hx, hz);
        if (len > 1e-6) { hx /= len; hz /= len; }
        else { hx = 0; hz = 0; }
        fx += hx * 1.05;
        fz += hz * 1.05;
        double horiz = Math.hypot(fx, fz);
        if (horiz > 2.6) { double s = 2.6 / horiz; fx *= s; fz *= s; }
        v.setX(fx);
        v.setZ(fz);
        v.setY(Math.max(v.getY(), 1.05));
        player.setVelocity(v);
        player.setFallDistance(0f);
        MomentumMaceFallProtection.grant(player, 1500L);
        MomentumMaceFx.vault(player);
    }

    public static void meteorDive(Player player) {
        MomentumMaceDiveTask.start(player);
        MomentumMaceFx.dive(player);
    }
}