package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Velocity-only movement for Kinetic Drive. No teleports, no permanent modifiers. */
public final class MomentumMaceMovement {
    private static final double VAULT_UP = 1.05;
    private static final double VAULT_FORWARD = 1.05;
    private static final double VAULT_RETENTION = 0.70;
    private static final double MAX_HORIZONTAL = 2.6;

    private MomentumMaceMovement() { }

    public static void vault(Player player) {
        Vector v = player.getVelocity();
        double fx = v.getX() * VAULT_RETENTION;
        double fz = v.getZ() * VAULT_RETENTION;
        Vector dir = player.getLocation().getDirection();
        double hx = dir.getX(), hz = dir.getZ();
        double len = Math.hypot(hx, hz);
        if (len > 1e-6) { hx /= len; hz /= len; } else { hx = 0; hz = 0; }
        fx += hx * VAULT_FORWARD;
        fz += hz * VAULT_FORWARD;
        double horiz = Math.hypot(fx, fz);
        if (horiz > MAX_HORIZONTAL) {
            double s = MAX_HORIZONTAL / horiz;
            fx *= s;
            fz *= s;
        }
        v.setX(fx);
        v.setZ(fz);
        v.setY(Math.max(v.getY(), VAULT_UP));
        player.setVelocity(v);
        player.setFallDistance(0f);
        MomentumMaceFallProtection.grant(player, 1500L);
        MomentumMaceFx.vault(player);
    }

    public static void meteorDive(Player player) {
        MomentumMaceFallProtection.grant(player, 2500L);
        MomentumMaceDiveTask.start(player);
        MomentumMaceFx.dive(player);
    }
}
