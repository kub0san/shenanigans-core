package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared, stateless helpers for constructing and posing {@link BlockDisplay} entities.
 *
 * <p>These were previously copy-pasted across the technique builders. Centralising
 * them here gives a single seam: bug fixes and tuning apply everywhere.</p>
 */
public final class BlockDisplayOps {

    private BlockDisplayOps() {}

    public static Quaternionf facing(Vector dir) {
        return new Quaternionf().rotationTo(0f, 0f, 1f,
                (float) dir.getX(), (float) dir.getY(), (float) dir.getZ());
    }

    public static Vector randomUnit(ThreadLocalRandom r) {
        double z = r.nextDouble() * 2.0 - 1.0;
        double a = r.nextDouble() * Math.PI * 2.0;
        double s = Math.sqrt(Math.max(0.0, 1.0 - z * z));
        return new Vector(s * Math.cos(a), z, s * Math.sin(a));
    }

    public static Location blockLoc(Location l) {
        return new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public static void shrink(BlockDisplay d, int ticks) {
        if (d == null || !d.isValid()) return;
        org.bukkit.util.Transformation t = d.getTransformation();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(new org.bukkit.util.Transformation(
                new Vector3f(t.getTranslation()).mul(0.04f),
                t.getLeftRotation(),
                new Vector3f(t.getScale()).mul(0.04f),
                t.getRightRotation()));
    }

    public static void line(BlockDisplay d, Location a, Location b, float thickness) {
        line(d, a, b, thickness, 1);
    }

    public static void line(BlockDisplay d, Location a, Location b, float thickness, int interp) {
        if (d == null || !d.isValid()) return;
        org.bukkit.util.Vector delta = b.toVector().subtract(a.toVector());
        double len = delta.length();
        if (len < 0.02) {
            place(d, a, new Quaternionf(), 0.02f, 0.02f, 0.02f, interp, interp);
            return;
        }
        Location mid = a.clone().add(delta.clone().multiply(0.5));
        place(d, mid, facing(delta.multiply(1.0 / len)), thickness, thickness, (float) len, interp, interp);
    }

    public static void place(BlockDisplay d, Location center, Quaternionf q,
                            float sx, float sy, float sz, int interp, int tele) {
        if (d == null || !d.isValid()) return;
        Vector3f off = new Vector3f(sx / 2f, sy / 2f, sz / 2f);
        q.transform(off);
        off.negate();
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(interp);
        d.setTransformation(new Transformation(off, new Quaternionf(q), new Vector3f(sx, sy, sz), new Quaternionf()));
        d.setTeleportDuration(tele);
        d.teleport(center);
    }
}
