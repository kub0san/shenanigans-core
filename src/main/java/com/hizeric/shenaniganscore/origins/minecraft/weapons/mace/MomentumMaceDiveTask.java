package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public final class MomentumMaceDiveTask extends BukkitRunnable {
    private final Player player;
    private final org.bukkit.World originWorld;
    private final Location start;
    private int ticks;
    private boolean impacted;
    private final int maxTicks = 30;

    private MomentumMaceDiveTask(Player player) {
        this.player = player;
        this.originWorld = player.getWorld();
        this.start = player.getLocation().clone();
    }

    public static void start(Player player) {
        new MomentumMaceDiveTask(player).runTaskTimer(
                com.hizeric.shenaniganscore.ShenanigansCore.getInstance(), 0L, 1L);
    }

    @Override
    public void run() {
        if (!player.isOnline() || player.isDead()) { finish(); return; }
        if (player.getWorld() != originWorld) { finish(); return; }
        if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) { finish(); return; }
        Location cur = player.getLocation();
        if (cur.distanceSquared(start) > 4096) { finish(); return; }
        ticks++;
        if (ticks > maxTicks) { finish(); return; }
        if (impacted) { finish(); return; }
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
        if (player.isOnGround()) {
            impacted = true;
            Vector rv = player.getVelocity();
            rv.setY(0.95);
            rv.setX(rv.getX() + hx * 0.3);
            rv.setZ(rv.getZ() + hz * 0.3);
            player.setVelocity(rv);
            player.setFallDistance(0f);
            MomentumMaceFx.dive(player);
            finish();
        }
    }

    private void finish() { try { cancel(); } catch (IllegalStateException ignored) { } }
}