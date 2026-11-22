package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.Location;

public final class MoveClamp {

    private MoveClamp() { }

    public static boolean outside(Location current, Location anchor, double radiusSquared) {
        if (current == null || anchor == null) return false;
        if (current.getWorld() != null && anchor.getWorld() != null
                && !current.getWorld().equals(anchor.getWorld())) return false;
        double dx = current.getX() - anchor.getX();
        double dy = current.getY() - anchor.getY();
        double dz = current.getZ() - anchor.getZ();
        return dx * dx + dy * dy + dz * dz > radiusSquared;
    }

    /** Projects {@code target} onto the sphere of radius {@code sqrt(radiusSquared)} around anchor. */
    public static Location clamp(Location target, Location anchor) {
        if (target == null || anchor == null) return target;
        double dx = target.getX() - anchor.getX();
        double dy = target.getY() - anchor.getY();
        double dz = target.getZ() - anchor.getZ();
        double distSq = dx * dx + dy * dy + dz * dz;
        if (distSq <= 0) return target.clone();

        double radius = Math.sqrt(2.25);
        double scale = radius / Math.sqrt(distSq);
        Location clamped = target.clone();
        clamped.setX(anchor.getX() + dx * scale);
        clamped.setY(anchor.getY() + dy * scale);
        clamped.setZ(anchor.getZ() + dz * scale);
        clamped.setYaw(target.getYaw());
        clamped.setPitch(target.getPitch());
        return clamped;
    }

    /** Snap-to-anchor fallback. */
    public static Location snap(Location target, Location anchor) {
        Location snapped = target.clone();
        snapped.setX(anchor.getX());
        snapped.setY(anchor.getY());
        snapped.setZ(anchor.getZ());
        snapped.setYaw(target.getYaw());
        snapped.setPitch(target.getPitch());
        return snapped;
    }
}