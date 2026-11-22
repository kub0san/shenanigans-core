package com.hizeric.shenaniganscore.core.session;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;


import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Centralized captive return/exit handling for domains.
 *
 * <p>Implements the return modes defined by {@link ReturnMode}. R2 is the
 * intended default (animated glide); this controller provides a practical
 * implementation that eases players back over a short duration when possible,
 * falling back to safe instant placement if scheduling/animation is not viable.</p>
 */
public final class DomainExitController {

    private DomainExitController() {}

    /** Performs the configured return for all captives of the session. */
    public static void returnCaptives(DomainSession session) {
        if (session == null) return;
        ReturnMode mode = session.returnMode() == null ? ReturnMode.R2 : session.returnMode();
        int duration = Math.max(1, session.returnDurationTicks());
        for (DomainSession.CaptiveRecord record : session.getCaptives().values()) {
            returnCaptive(session, record, mode, duration);
        }
        session.releaseAllCaptives();
    }

    /** Returns a single captive according to the given mode. */
    public static void returnCaptive(DomainSession session, DomainSession.CaptiveRecord record, ReturnMode mode) {
        int duration = session == null ? 20 : Math.max(1, session.returnDurationTicks());
        returnCaptive(session, record, mode, duration);
    }

    private static void returnCaptive(DomainSession session, DomainSession.CaptiveRecord record, ReturnMode mode, int durationTicks) {
        if (session == null || record == null) return;
        Player player = Bukkit.getPlayer(record.uuid());
        if (player == null || !player.isOnline()) return;

        Location target = resolveReturnLocation(session, record, mode);
        if (target == null || target.getWorld() == null) return;

        switch (mode) {
            case R2 -> glideReturn(player, target, durationTicks, session.returnEasing());
            case R4 -> twoPhaseReturn(session, player, target);
            case R1, R3, EXACT_REWIND -> safeTeleport(player, target);
            default -> safeTeleport(player, target);
        }
    }

    /** Resolves the destination location for a captive under the chosen mode. */
    public static Location resolveReturnLocation(DomainSession session, DomainSession.CaptiveRecord record, ReturnMode mode) {
        if (record == null) return null;
        Location original = record.captureLocation() == null ? null : record.captureLocation().clone();
        if (original == null || original.getWorld() == null) return original;

        return switch (mode == null ? ReturnMode.R2 : mode) {
            case EXACT_REWIND -> original;
            case R1, R2, R3, R4 -> findSafeEquivalent(session, original, record);
        };
    }

    /**
     * Finds a safe equivalent of the original capture location. Prefers the
     * original if safe; otherwise nudges upward to the nearest passable space
     * in the same column (or just above the domain plane if needed).
     */
    public static Location findSafeEquivalent(DomainSession session, Location original, DomainSession.CaptiveRecord record) {
        if (original == null || original.getWorld() == null) return original;
        World world = original.getWorld();
        Location candidate = original.clone();

        // If original feet space is passable enough, keep it.
        if (isStandable(world, candidate)) {
            return candidate;
        }

        int x = candidate.getBlockX();
        int z = candidate.getBlockZ();
        int minY = Math.max(world.getMinHeight() + 1, candidate.getBlockY());
        int maxY = world.getMaxHeight() - 2;

        // Try moving up from original/capture to find first standable spot.
        for (int y = minY; y <= maxY; y++) {
            Location probe = new Location(world, candidate.getX(), y + 0.0, candidate.getZ(),
                    candidate.getYaw(), candidate.getPitch());
            if (isStandable(world, probe)) {
                return probe;
            }
        }

        // Fallback: above plane Y if inside domain context.
        if (session != null) {
            double safeY = Math.min(maxY, session.getPlaneY() + 1.0);
            Location abovePlane = new Location(world, candidate.getX(), safeY, candidate.getZ(),
                    candidate.getYaw(), candidate.getPitch());
            if (isStandable(world, abovePlane)) return abovePlane;
            return abovePlane;
        }

        return candidate;
    }

    private static boolean isStandable(World world, Location feet) {
        if (world == null || feet == null) return false;
        var feetBlock = world.getBlockAt(feet.getBlockX(), feet.getBlockY(), feet.getBlockZ());
        var headBlock = world.getBlockAt(feet.getBlockX(), feet.getBlockY() + 1, feet.getBlockZ());
        var below = world.getBlockAt(feet.getBlockX(), feet.getBlockY() - 1, feet.getBlockZ());
        boolean spaceClear = feetBlock.isPassable() && headBlock.isPassable();
        boolean hasGround = below.getType().isSolid() || !below.isPassable();
        return spaceClear && hasGround;
    }

    private static void safeTeleport(Player player, Location target) {
        if (player == null || !player.isOnline() || target == null || target.getWorld() == null) return;
        try {
            player.teleport(target);
            player.setFallDistance(0.0f);
        } catch (Throwable ignored) {
            // Never let a return failure break domain teardown.
        }
    }

    /**
     * R2-style animated glide: eases the player from current position to target
     * over {@code ticks}. If scheduling is unavailable, falls back to instant.
     */
    private static void glideReturn(Player player, Location target, int ticks, String easing) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || !plugin.isEnabled() || ticks <= 0) {
            safeTeleport(player, target);
            return;
        }
        if (!player.isOnline()) return;
        Location start = player.getLocation().clone();
        if (start.getWorld() == null || target.getWorld() == null || !start.getWorld().equals(target.getWorld())) {
            safeTeleport(player, target);
            return;
        }
        final String curve = easing == null ? "smoothstep" : easing.trim().toLowerCase();
        try {
            new BukkitRunnable() {
                int t = 0;

                @Override
                public void run() {
                    if (!player.isOnline()) {
                        cancel();
                        return;
                    }
                    t++;
                    double p = MathUtil.clamp(t / (double) ticks, 0.0, 1.0);
                    double eased = switch (curve) {
                        case "linear" -> p;
                        case "ease-in-out", "cubic", "cubic-bezier" -> MathUtil.smoothstep(p);
                        case "ease-out" -> 1.0 - (1.0 - p) * (1.0 - p);
                        default -> MathUtil.smoothstep(p); // smoothstep fallback
                    };
                    double x = MathUtil.lerp(start.getX(), target.getX(), eased);
                    double y = MathUtil.lerp(start.getY(), target.getY(), eased);
                    double z = MathUtil.lerp(start.getZ(), target.getZ(), eased);
                    float yaw = (float) MathUtil.lerp(start.getYaw(), target.getYaw(), eased);
                    float pitch = (float) MathUtil.lerp(start.getPitch(), target.getPitch(), eased);
                    Location step = new Location(target.getWorld(), x, y, z, yaw, pitch);
                    try {
                        player.teleport(step);
                        player.setFallDistance(0.0f);
                    } catch (Throwable ignored) {
                        // A single glide-step teleport failure must not abort the whole return sequence.
                    }
                    if (t >= ticks) {
                        safeTeleport(player, target);
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        } catch (RuntimeException ex) {
            safeTeleport(player, target);
        }
    }

    /**
     * R4 two-phase handoff: move out immediately to a safe spot now, then resolve
     * final position shortly after (best-effort).
     */
    private static void twoPhaseReturn(DomainSession session, Player player, Location target) {
        // Phase 1: immediate safe move out/resolve to avoid flicker with restoration.
        safeTeleport(player, target);
        // Phase 2: deferred settle after domain teardown window.
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null || !plugin.isEnabled()) return;
        try {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || target == null || target.getWorld() == null) return;
                    Location settled = session == null ? target : findSafeEquivalent(session, target, null);
                    safeTeleport(player, settled == null ? target : settled);
                }
            }.runTaskLater(plugin, 10L);
        } catch (RuntimeException ignored) {
            // intentional: the retry task is best-effort; the caller already returned the player.
        }
    }
}