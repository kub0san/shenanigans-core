package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin Bukkit driver for the Rift Cleave dash-sword sequence.
 *
 * <p>All timing and geometry comes from {@link DashAnimation}; this class only turns frames into
 * displays, particles, sounds and a one-tick shake. Every display spawned here is tracked and
 * removed when the timeline ends, and all sounds are played at the player's location.</p>
 */
public final class DashSwordEffect {

    private static final double FORWARD_MOMENTUM = 2.6;
    private static final double LIFT = 0.35;
    private static final double MAX_UPWARD_MOMENTUM = 0.95;

    private static final int SPEED_DURATION_TICKS = 60;
    private static final int SPEED_AMPLIFIER = 1; // Speed II

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
        Vector momentum = direction.clone().multiply(FORWARD_MOMENTUM);
        double upward = Math.min(Math.max(momentum.getY(), -0.3) + LIFT, MAX_UPWARD_MOMENTUM);
        momentum.setY(upward);

        player.setVelocity(momentum);
        player.setFallDistance(0.0f);
        player.setSprinting(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS,
                SPEED_AMPLIFIER, true, false, false));
    }

    /** One runnable drives both the launch and trail frames so timing cannot drift apart. */
    private static final class Timeline extends BukkitRunnable {

        private final Player player;
        private final Vector forward;
        private final Vector right;
        private final Vector up;
        private final Location origin;
        private final List<DashAnimation.Frame> launch = DashAnimation.launch();
        private final List<DashAnimation.Frame> trail = DashAnimation.trail();
        private final List<ItemDisplay> displays = new ArrayList<>();
        private final MannequinEchoRenderer echoes;
        private final DashVisibilityMask visibility;
        private final List<Ring> rings = new ArrayList<>();

        private Vector shakeRestore;
        private int tick;
        private boolean cleared;

        private Timeline(Player player, Vector forward) {
            this.player = player;
            this.echoes = new MannequinEchoRenderer(player);
            this.visibility = new DashVisibilityMask(player);
            this.forward = forward.clone().normalize();
            this.right = DashAnimation.rightOf(this.forward);
            this.up = this.right.clone().crossProduct(this.forward).normalize();
            this.origin = player.getLocation().clone().add(0, 0.9, 0);
        }

        @Override
        public void run() {
            if (tick > DashAnimation.TRAIL_TICKS || !player.isOnline() || player.isDead()) {
                clear();
                cancel();
                return;
            }

            // A shake lasts exactly one tick: restore the velocity captured on the previous
            // shake frame before doing anything else this tick.
            if (shakeRestore != null) {
                player.setVelocity(shakeRestore);
                shakeRestore = null;
            }

            DashAnimation.Frame launchFrame = launch.get(Math.min(tick, launch.size() - 1));
            DashAnimation.Frame trailFrame = trail.get(Math.min(tick, trail.size() - 1));

            if (launchFrame.swing()) player.swingMainHand();
            for (DashAnimation.DisplaySpawn spawn : launchFrame.spawns()) spawnLaunch(spawn);
            for (DashAnimation.Sound sound : launchFrame.sounds()) play(sound);
            if (launchFrame.shake()) shake();

            for (DashAnimation.DisplaySpawn spawn : trailFrame.spawns()) spawnEchoes(spawn);
            echoes.tick(tick);
            visibility.tick();
            animateRings();
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
                    // The verbatim launch phase emitted this cue but never spawned a display.
                    for (int i = 0; i < spawn.count(); i++) {
                        ItemDisplay display = DisplayUtils.spawnItem(
                                ringPoint(origin, (Math.PI * 2.0 * i) / spawn.count(), 0.35),
                                new ItemStack(Material.WIND_CHARGE), options());
                        TransformUtils.applyTransform(display, spawn.scale(), spawn.scale(),
                                spawn.scale(), 0f, 0f, 0f, 1);
                        displays.add(display);
                        rings.add(new Ring(display, tick, i, spawn.count(), spawn.scale()));
                    }
                }
                case GHOST_ARC -> {
                    // Ghost blades belong to the trail timeline, never the launch timeline.
                }
            }
        }

        private void spawnEchoes(DashAnimation.DisplaySpawn spawn) {
            Location at = player.getLocation().clone().add(0, 0.9, 0);
            Location behind = at.clone().subtract(forward.clone().multiply(0.4));
            for (int i = 0; i < spawn.count(); i++) {
                // Straight-line streak: every echo sits just behind the caster along the dash
                // direction, with no lateral offset — a motion trail, never flanking clones.
                echoes.spawn(behind, at.getYaw(), at.getPitch(), tick);
                player.getWorld().spawnParticle(Particle.CLOUD, behind, 2, 0.06, 0.06, 0.06, 0.01);
            }
        }

        private void animateRings() {
            for (Ring ring : rings) {
                int age = tick - ring.bornTick();
                float scale = Math.max(0.02f, ring.baseScale() - age * 0.045f);
                double radius = 0.35 + age * 0.14;
                Location point = ringPoint(origin, (Math.PI * 2.0 * ring.index()) / ring.total(),
                        radius).add(forward.clone().multiply(0.15));
                ring.display().teleport(point);
                TransformUtils.applyTransform(ring.display(), scale, scale, scale, 0f, 0f, 0f, 1);
            }
        }

        private void shake() {
            Vector velocity = player.getVelocity();
            shakeRestore = velocity.clone();
            Vector jitter = new Vector((Math.random() - 0.5) * 0.35,
                    (Math.random() - 0.5) * 0.12,
                    (Math.random() - 0.5) * 0.35);
            player.setVelocity(velocity.add(jitter));
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

        private Location ringPoint(Location center, double angle, double radius) {
            return center.clone()
                    .add(right.clone().multiply(Math.cos(angle) * radius))
                    .add(up.clone().multiply(Math.sin(angle) * radius));
        }

        private void clear() {
            if (cleared) return;
            cleared = true;
            for (ItemDisplay display : displays) remove(display);
            displays.clear();
            echoes.close();
            visibility.restore();
            rings.clear();
        }
    }

    private record Ring(ItemDisplay display, int bornTick, int index, int total, float baseScale) { }

    private static DisplayUtils.Options options() {
        return DisplayUtils.Options.builder()
                .viewRange(8.0f)
                .interpolation(0, 1)
                .teleportDuration(1)
                .build();
    }

    private static void remove(ItemDisplay display) {
        if (display != null && display.isValid()) display.remove();
    }
}
