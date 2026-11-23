package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Thin Bukkit driver for the Rift Cleave dash-sword sequence.
 *
 * <p>All timing and geometry comes from {@link DashAnimation}; this class turns frames into
 * particles, sounds, mannequin echoes and display-entity wind visuals. The dash follows a locked
 * straight line — including downward — and the timeline ends early once that motion stops. Every
 * spawned effect is owned by a helper and removed when the timeline ends.</p>
 */
public final class DashSwordEffect {

    private static final double FORWARD_MOMENTUM = 2.6;

    /** A one-tick move this far can only be a teleport or a round reset, not a dash step. */
    private static final double MAX_STEP = 8.0;
    /** A stuck player keeps a large re-applied velocity while their position barely changes. */
    private static final double STALL_DELTA = 0.05;
    private static final int STALL_TICKS = 2;

    private DashSwordEffect() { }

    public static void launch(Player player) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (player == null || plugin == null) return;

        Vector direction = player.getEyeLocation().getDirection();
        if (direction.lengthSquared() < 1.0e-6) direction = new Vector(0, 0, 1);
        direction.normalize();

        applyMomentum(player, direction);
        new Timeline(player, direction).runTaskTimer(plugin, 0L, 1L);
    }

    private static void applyMomentum(Player player, Vector direction) {
        // No lift, no downward clamp: looking down dashes down. Re-applied every timeline tick.
        player.setVelocity(direction.clone().multiply(FORWARD_MOMENTUM));
        player.setFallDistance(0.0f);
        player.setSprinting(true);
    }

    /** One runnable drives launch, trail and teardown so timing cannot drift apart. */
    private static final class Timeline extends BukkitRunnable {

        private final Player player;
        private final Vector forward;
        private final Location origin;
        private final Location echoOrigin;
        private final World world;
        private final GameMode launchGameMode;
        private Location lastPosition;
        private final float launchYaw;
        private final float launchPitch;
        private final List<DashAnimation.Frame> launch = DashAnimation.launch();
        private final List<DashAnimation.Frame> trail = DashAnimation.trail();
        private final MannequinEchoRenderer echoes;
        private final DashVisibilityMask visibility;
        private final WindTrailEffect wind;

        private int tick;
        private int stallTicks;
        private boolean airborneOnce;
        private boolean windBurstLaunched;
        private boolean cleared;

        private Timeline(Player player, Vector forward) {
            this.player = player;
            this.forward = forward.clone().normalize();
            this.origin = player.getLocation().clone().add(0, 0.9, 0);
            this.echoOrigin = this.origin.clone();
            this.world = player.getWorld();
            this.launchGameMode = player.getGameMode();
            this.lastPosition = player.getLocation().clone();
            this.launchYaw = player.getLocation().getYaw();
            this.launchPitch = player.getLocation().getPitch();
            this.echoes = new MannequinEchoRenderer(player);
            this.visibility = new DashVisibilityMask(player);
            this.wind = new WindTrailEffect(player, origin, forward);
        }

        @Override
        public void run() {
            if (tick > DashAnimation.TRAIL_TICKS || !player.isOnline() || player.isDead()
                    || player.getWorld() != world || player.getGameMode() != launchGameMode) {
                finish();
                return;
            }

            Location current = player.getLocation();
            if (current.distanceSquared(lastPosition) > MAX_STEP * MAX_STEP) {
                // Teleport, staging back to the hub or any other context jump ends the dash here.
                finish();
                return;
            }

            boolean onGround = player.isOnGround();
            if (onGround && airborneOnce) {
                finish();
                return;
            }
            if (!onGround) airborneOnce = true;

            // Velocity is re-applied every tick, so a player stuck against a wall still carries a
            // large velocity while their position barely changes. Stall detection reads position.
            double moved = current.distance(lastPosition);
            if (tick > 1 && moved < STALL_DELTA) stallTicks++;
            else stallTicks = 0;
            if (stallTicks >= STALL_TICKS) {
                finish();
                return;
            }
            lastPosition = current.clone();

            // Gravity and knockback must not bend the locked path — including a downward dash.
            player.setVelocity(forward.clone().multiply(FORWARD_MOMENTUM));

            DashAnimation.Frame launchFrame = launch.get(Math.min(tick, launch.size() - 1));
            DashAnimation.Frame trailFrame = trail.get(Math.min(tick, trail.size() - 1));

            if (launchFrame.swing()) player.swingMainHand();
            for (DashAnimation.DisplaySpawn spawn : launchFrame.spawns()) spawnLaunch(spawn);
            for (DashAnimation.Sound sound : launchFrame.sounds()) play(sound);

            for (DashAnimation.DisplaySpawn spawn : trailFrame.spawns()) spawnEchoes(spawn);
            echoes.tick(tick);
            visibility.tick();
            wind.tick(tick);
            tick++;
        }

        private void spawnLaunch(DashAnimation.DisplaySpawn spawn) {
            switch (spawn.kind()) {
                case CLEAVE_BURST -> {
                    player.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                            origin.clone().add(forward.clone().multiply(0.9)), 1, 0, 0, 0, 0);
                    player.getWorld().spawnParticle(Particle.CLOUD, origin,
                            spawn.count(), 0.02, 0.02, 0.02, 0.01);
                }
                case RIFT_RIBBON -> player.getWorld().spawnParticle(Particle.END_ROD,
                        origin.clone().add(forward.clone().multiply(0.15 * tick)),
                        spawn.count(), 0.05, 0.05, 0.05, 0.01);
                case WIND_CHARGE -> {
                    // The beat fires on three frames, but the burst is a single launch cue.
                    if (!windBurstLaunched) {
                        wind.launchBurst(tick);
                        windBurstLaunched = true;
                    }
                }
                case GHOST_ARC -> {
                    // Ghost blades belong to the trail timeline, never the launch timeline.
                }
            }
        }

        private void spawnEchoes(DashAnimation.DisplaySpawn spawn) {
            // The trail follows the locked launch line, not the live camera or a turned head.
            Location point = echoOrigin.clone()
                    .add(forward.clone().multiply(FORWARD_MOMENTUM * tick));
            for (int i = 0; i < spawn.count(); i++) {
                echoes.spawn(point, launchYaw, launchPitch, tick);
                player.getWorld().spawnParticle(Particle.CLOUD, point, 2, 0.06, 0.06, 0.06, 0.01);
            }
            wind.trailStreak(point, tick);
        }

        private void play(DashAnimation.Sound cue) {
            Sound sound = switch (cue.cue()) {
                case SWING -> Sound.ENTITY_BREEZE_SHOOT;
                case RIFT_BREATH -> Sound.ITEM_TRIDENT_RIPTIDE_3;
                case RIFT_CLOSE -> Sound.ENTITY_PLAYER_ATTACK_SWEEP;
            };
            // The verbatim played sounds at the world origin; audible playback is at the player.
            SoundUtils.play(player.getLocation(), sound, cue.volume(), cue.pitch());
        }

        private void finish() {
            clear();
            cancel();
        }

        private void clear() {
            if (cleared) return;
            cleared = true;
            echoes.close();
            visibility.restore();
            wind.close();
        }
    }
}
