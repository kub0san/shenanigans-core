package com.hizeric.shenaniganscore.battleground.deploy;

import com.hizeric.shenaniganscore.battleground.BattlegroundDefaults;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Drives one player's "Starfall Lock" drop: a 15-tick lock-on held with gravity off, a variable-length
 * descent (velocity ramp, zero horizontal drift, fall distance zeroed), and a 20-tick impact
 * afterglow. All geometry comes from the pure {@link DropAnimation} frames; this is the only class
 * that talks to Bukkit. The lock-on ring and text display are visible to nearby players, so the
 * animation itself advertises an incoming drop.
 */
public final class SkyDrop {

    @FunctionalInterface
    public interface LiquidContactHandler {
        void onLiquid(Player player);
    }

    private enum Phase { LOCK, DIVE, IMPACT }

    private final Plugin plugin;
    private final DiveService dives;
    private final LiquidContactHandler liquidHandler;
    private final Consumer<Player> abortHandler;
    // One task and one display set per diver, so player-state cleanup can stop a dive without
    // waiting for its animation to run to completion. The player handle lets cancel() undo the
    // physical state begin() changed, even when the task has already stopped (e.g. finish()).
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final Map<UUID, Map<Display, Long>> displaysByUuid = new HashMap<>();
    /** Hard upper bound on live display entities per dive (Starfall Lock #9). */
    private static final int MAX_DISPLAY_POOL = 32;
    private final Map<UUID, Player> players = new HashMap<>();

    public SkyDrop(Plugin plugin, DiveService dives, LiquidContactHandler liquidHandler,
                   Consumer<Player> abortHandler) {
        this.plugin = plugin;
        this.dives = dives;
        this.liquidHandler = liquidHandler;
        this.abortHandler = abortHandler;
        // Registration seam: DiveService.clear(UUID) / clear() call back into cancel without
        // BattlegroundManager having to wire the two collaborators together after construction.
        if (dives != null) dives.attach(this);
    }

    /**
     * Stops one player's dive: cancels its scheduler task, removes its displays, restores the
     * gravity and fall-distance state changed by {@link #begin}, and releases the dive lock.
     * Idempotent, and safe to call from {@link DiveService#clear(UUID)} as well as from cleanup
     * paths that call this directly. Velocity is deliberately left untouched: the natural landing
     * path ({@code finish(true)}) preserves momentum across the {@code dives.clear(uuid)} callback.
     */
    public void cancel(UUID uuid) {
        if (uuid == null) return;
        BukkitTask task = tasks.remove(uuid);
        if (task != null) task.cancel();
        Map<Display, Long> displays = displaysByUuid.remove(uuid);
        if (displays != null) {
            for (Display display : displays.keySet()) display.remove();
            displays.clear();
        }
        Player player = players.remove(uuid);
        if (player != null && player.isOnline()) {
            player.setGravity(true);
            player.setFallDistance(0f);
        }
        // clear(UUID) drops the flag before calling back here; the guard also breaks the loop
        // when cancel(UUID) is the entry point instead.
        if (dives.isDiving(uuid)) dives.clear(uuid);
    }

    /** Used by {@link DiveService#clear()} so every tracked dive goes through {@link #cancel(UUID)}. */
    void cancelAll() {
        for (UUID uuid : new ArrayList<>(tasks.keySet())) cancel(uuid);
        for (UUID uuid : new ArrayList<>(displaysByUuid.keySet())) cancel(uuid);
        for (UUID uuid : new ArrayList<>(players.keySet())) cancel(uuid);
    }

    /**
     * @param start already teleported to by the caller; {@code groundY} is the surface under the target.
     *              The caller must teleport before calling this — the first 15 ticks have no downward
     *              velocity at all.
     */
    public void begin(Player player, Location start, int groundY) {
        final UUID uuid = player == null ? null : player.getUniqueId();
        // Evict a stale dive on every begin path, including an invalid start: otherwise its task,
        // displays and gravity-off state would be orphaned when the guard below aborts.
        if (uuid != null) cancel(uuid);
        if (player == null || start == null || start.getWorld() == null) {
            if (player != null && abortHandler != null && player.isOnline()) abortHandler.accept(player);
            return;
        }
        final World world = start.getWorld();
        final double gx = start.getX();
        final double gz = start.getZ();
        final DropProbe probe = new BukkitDropProbe(world);

        final Map<Display, Long> despawnAtTick = new HashMap<>();
        displaysByUuid.put(uuid, despawnAtTick);
        players.put(uuid, player);
        dives.mark(uuid);
        player.setGravity(false);
        player.setFallDistance(0f);
        player.setVelocity(new Vector(0, 0, 0));

        BukkitRunnable runnable = new BukkitRunnable() {
            int tick = 0;
            int diveTick = 0;
            Phase phase = Phase.LOCK;
            Location last = start.clone();

            @Override
            public void run() {
                // The diving flag is the single source of truth: a cleared dive never ticks again,
                // even if a scheduler callback was already queued when cleanup happened.
                if (!dives.isDiving(uuid)) {
                    SkyDrop.this.cancel(uuid);
                    return;
                }
                if (!player.isOnline() || player.isDead() || player.getWorld() != world) {
                    finish(false);
                    cancel();
                    return;
                }
                // Moved more than 10 blocks in a tick: something else teleported them, back off.
                if (player.getLocation().distanceSquared(last) > 100) {
                    finish(false);
                    cancel();
                    if (abortHandler != null && player.isOnline()) abortHandler.accept(player);
                    return;
                }
                last = player.getLocation().clone();
                tick++;

                switch (phase) {
                    case LOCK -> {
                        // Held in place: no gravity, no drift, no fall damage accumulating.
                        player.setVelocity(new Vector(0, 0, 0));
                        player.setFallDistance(0f);
                        if (tick >= DropAnimation.LOCK_TICKS) {
                            phase = Phase.DIVE;
                            player.setGravity(true);
                            diveTick = 0;
                        } else {
                            apply(DropAnimation.lockOn(gx, player.getY(), gz, tick),
                                    world, gx, player.getY(), gz, despawnAtTick);
                        }
                    }
                    case DIVE -> {
                        Location at = player.getLocation();
                        DiveContact.Kind contact = DiveContact.classify(
                                player.isOnGround(),
                                at.getBlock().isLiquid(),
                                at.clone().add(0, 1, 0).getBlock().isLiquid());
                        if (diveTick > BattlegroundDefaults.DEPLOY_LANDING_ARM_TICKS
                                && contact != DiveContact.Kind.NONE) {
                            boolean liquid = contact == DiveContact.Kind.LIQUID;
                            finish(!liquid);
                            cancel();
                            if (liquid) {
                                if (liquidHandler != null) liquidHandler.onLiquid(player);
                            }
                            return;
                        }
                        // Trace the column a few blocks below the diver, not just the landing frame.
                        // Any non-air block above the scored surface means the path was mis-scored.
                        if (pathBlocked(probe, at, groundY)) {
                            finish(false);
                            cancel();
                            if (abortHandler != null && player.isOnline()) abortHandler.accept(player);
                            return;
                        }
                        DiveMotion.Velocity v = DiveMotion.forTick(diveTick,
                                BattlegroundDefaults.DEPLOY_DOWN_SPEED);
                        player.setVelocity(new Vector(v.x(), v.y(), v.z()));
                        player.setFallDistance(0f);
                        apply(DropAnimation.descent(gx, player.getY(), gz, diveTick),
                                world, gx, player.getY(), gz, despawnAtTick);
                        diveTick++;
                        if (diveTick >= BattlegroundDefaults.DEPLOY_MAX_TICKS) {
                            finish(false);
                            cancel();
                            if (abortHandler != null && player.isOnline()) abortHandler.accept(player);
                        }
                    }
                    case IMPACT -> {
                        apply(DropAnimation.impact(gx, player.getY(), gz, diveTick),
                                world, gx, player.getY(), gz, despawnAtTick);
                        diveTick++;
                        if (diveTick >= DropAnimation.IMPACT_TICKS) {
                            finish(true);
                            cancel();
                        }
                    }
                }

                long now = world.getGameTime();
                despawnAtTick.entrySet().removeIf(entry -> {
                    if (now >= entry.getValue()) {
                        entry.getKey().remove();
                        return true;
                    }
                    return false;
                });
            }

            private void finish(boolean landed) {
                dives.clear(uuid);
                if (player.isOnline()) {
                    player.setGravity(true);
                    player.setFallDistance(0f);
                    if (!landed) player.setVelocity(new Vector(0, 0, 0));
                }
                for (Display display : despawnAtTick.keySet()) display.remove();
                despawnAtTick.clear();
            }
        };
        tasks.put(uuid, runnable.runTaskTimer(plugin, 1L, 1L));
    }

    /** True when a non-air block sits in the next few blocks of the descent column. */
    private static boolean pathBlocked(DropProbe probe, Location at, int groundY) {
        int blockX = at.getBlockX();
        int blockZ = at.getBlockZ();
        int feet = at.getBlockY();
        int lowest = Math.max(groundY + 1, feet - 4);
        for (int y = feet; y >= lowest; y--) {
            if (!probe.isAirLike(blockX, y, blockZ)) return true;
        }
        return false;
    }

    private void apply(DropAnimation.Frame frame, World world, double x, double y, double z,
                       Map<Display, Long> despawnAtTick) {
        for (DropAnimation.Beat beat : frame.beats()) {
            Particle particle = map(beat.kind());
            if (particle == null) continue;
            Object extra = extra(beat);
            if (extra == null) {
                world.spawnParticle(particle, beat.x(), beat.y(), beat.z(), beat.count(),
                        beat.dx(), beat.dy(), beat.dz(), beat.speed());
            } else {
                world.spawnParticle(particle, beat.x(), beat.y(), beat.z(), beat.count(),
                        beat.dx(), beat.dy(), beat.dz(), beat.speed(), extra);
            }
        }
        // Cues belong at the drop, not at the world origin.
        Location at = new Location(world, x, y, z);
        for (DropAnimation.Sound cue : frame.sounds()) {
            Sound sound = sound(cue.key());
            if (sound != null) world.playSound(at, sound, cue.volume(), cue.pitch());
        }
        for (DropAnimation.Display display : frame.displays()) {
            // Reuse a display whose despawn time has passed instead of spawning a new one;
            // hard-cap the live pool at MAX_DISPLAY_POOL to bound entity churn per dive.
            TextDisplay reused = null;
            long now = world.getGameTime();
            for (Map.Entry<Display, Long> entry : despawnAtTick.entrySet()) {
                if (entry.getValue() <= now && entry.getKey() instanceof TextDisplay td
                        && td.isValid()) {
                    reused = td;
                    break;
                }
            }
            if (reused == null && despawnAtTick.size() >= MAX_DISPLAY_POOL) {
                // Pool full: skip this beat's display rather than growing unbounded.
                continue;
            }
            final TextDisplay text;
            if (reused != null) {
                despawnAtTick.remove(reused);
                reused.teleport(new Location(world, display.x(), display.y(), display.z()));
                reused.setText(display.text());
                float scale = display.scale();
                reused.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                        new Vector3f(scale, scale, scale), new Quaternionf()));
                text = reused;
            } else {
                text = world.spawn(new Location(world, display.x(), display.y(), display.z()),
                        TextDisplay.class, entity -> {
                            entity.setText(display.text());
                            entity.setBillboard(Display.Billboard.CENTER);
                            entity.setSeeThrough(true);
                            entity.setShadowed(true);
                            entity.setViewRange(64f);
                            float scale = display.scale();
                            entity.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                                    new Vector3f(scale, scale, scale), new Quaternionf()));
                        });
            }
            despawnAtTick.put(text, now + display.lifeTicks());
        }
    }

    private static Particle map(DropAnimation.Kind kind) {
        return switch (kind) {
            case DUST -> Particle.DUST;
            case DUST_TRANSITION -> Particle.DUST_COLOR_TRANSITION;
            case END_ROD -> Particle.END_ROD;
            case SOUL_FLAME -> Particle.SOUL_FIRE_FLAME;
            case ELECTRIC_SPARK -> Particle.ELECTRIC_SPARK;
            case SONIC_BOOM -> Particle.SONIC_BOOM;
            case EXPLOSION -> Particle.EXPLOSION;
            case FLASH -> Particle.FLASH;
            case CLOUD -> Particle.CLOUD;
            case ENCHANT -> Particle.ENCHANT;
            case WAX -> Particle.WAX_ON;
            case TOTEM -> Particle.TOTEM_OF_UNDYING;
        };
    }

    private static Object extra(DropAnimation.Beat beat) {
        if (beat.kind() == DropAnimation.Kind.DUST_TRANSITION) {
            return new Particle.DustTransition(Color.fromRGB(beat.rgbA()),
                    Color.fromRGB(beat.rgbB()), beat.size());
        }
        if (beat.kind() == DropAnimation.Kind.DUST) {
            return new Particle.DustOptions(Color.fromRGB(beat.rgbA()), beat.size());
        }
        return null;
    }

    private static Sound sound(String key) {
        try {
            return Sound.valueOf(key);
        } catch (IllegalArgumentException ignored) {
            return null; // a sound may be absent on older Paper builds; skip the cue
        }
    }
}
