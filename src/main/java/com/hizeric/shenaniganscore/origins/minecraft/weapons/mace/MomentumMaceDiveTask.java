package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Bounded, single-owner Meteor Dive. Ends idempotently and never leaves movement modifiers. */
public final class MomentumMaceDiveTask extends BukkitRunnable {
    private static final Map<UUID, MomentumMaceDiveTask> ACTIVE = new ConcurrentHashMap<>();
    private static final int MAX_TICKS = 30;

    private enum State { PREPARE, DIVE, DONE }
    private final Player player;
    private final World originWorld;
    private final Location start;
    private State state = State.PREPARE;
    private boolean airborneSeen;
    private int ticks;

    private MomentumMaceDiveTask(Player player) {
        this.player = player;
        this.originWorld = player.getWorld();
        this.start = player.getLocation().clone();
    }

    public static void start(Player player) {
        MomentumMaceDiveTask old = ACTIVE.remove(player.getUniqueId());
        if (old != null) old.finish();
        MomentumMaceDiveTask task = new MomentumMaceDiveTask(player);
        ACTIVE.put(player.getUniqueId(), task);
        task.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    @Override
    public void run() {
        if (!owns(player)) { finish(); return; }
        ticks++;
        if (ticks > MAX_TICKS) { finish(); return; }

        if (state == State.PREPARE) {
            // Grounded casts get a tiny takeoff so the starting ground is never mistaken for impact.
            Vector v = player.getVelocity();
            v.setY(Math.max(v.getY(), 0.6));
            player.setVelocity(v);
            player.setFallDistance(0f);
            state = State.DIVE;
            return;
        }
        if (state != State.DIVE) { finish(); return; }

        Vector v = player.getVelocity();
        Vector dir = player.getLocation().getDirection();
        double hx = dir.getX(), hz = dir.getZ();
        double len = Math.hypot(hx, hz);
        if (len > 1e-6) { hx /= len; hz /= len; } else { hx = 0; hz = 0; }
        v.setX(v.getX() * 0.5 + hx * 1.15);
        v.setZ(v.getZ() * 0.5 + hz * 1.15);
        v.setY(Math.min(v.getY(), -1.75));
        player.setVelocity(v);
        player.setFallDistance(0f);
        if (!player.isOnGround()) airborneSeen = true;
        if (airborneSeen && player.isOnGround()) {
            Vector rv = player.getVelocity();
            rv.setY(0.95);
            rv.setX(rv.getX() + hx * 0.3);
            rv.setZ(rv.getZ() + hz * 0.3);
            player.setVelocity(rv);
            player.setFallDistance(0f);
            MomentumMaceFx.impact(player);
            state = State.DONE;
            finish();
        }
    }

    private boolean owns(Player p) {
        if (!p.isOnline() || p.isDead() || p.getWorld() != originWorld) return false;
        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return false;
        if (p.isInsideVehicle() || p.isFlying() || p.isGliding()) return false;
        if (p.getLocation().distanceSquared(start) > 4096) return false;
        return ACTIVE.get(p.getUniqueId()) == this;
    }

    private void finish() {
        state = State.DONE;
        ACTIVE.remove(player.getUniqueId(), this);
        try { cancel(); } catch (IllegalStateException ignored) { }
    }
}
