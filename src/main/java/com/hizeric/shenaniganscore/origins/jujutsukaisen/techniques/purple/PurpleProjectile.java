package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlowness;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlownessCleanup;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Hollow Technique: Purple — the state machine.
 *
 * <p><b>Design (from the lore):</b> Purple is what you get when Blue (attraction) and Red (repulsion) are
 * driven into the same space at the same time. They do not cancel; they make an <i>imaginary mass</i>
 * that rushes forward and erases everything in its path, ignoring ordinary durability. So this
 * technique is literally built from the other two:</p>
 *
 * <pre>
 *  CONJURE  ~0.7s   Blue forms at the left hand, Red at the right (the same displays the Blue and Red techniques produce).
 *  SEND             (remote only) the two are thrown apart and curve toward the crosshair.
 *  FUSE     ~0.6s   the two orbit each other tighter and tighter, then collide. The same displays are
 *                   morphed to purple; Red's crown rays and Blue's accretion cubes stay, now twisting
 *                   around the new core.
 *  FIRE             (standard) the imaginary mass accelerates forward through the world. It cannot be
 *                   blocked: terrain along its path is erased (client-side) and every living thing in
 *                   the tube takes one huge armor-piercing hit.
 *  BLAST            (remote) the fused point detonates into an expanding sphere of erasure.
 *  COLLAPSE ~0.7s   the imaginary mass folds in on itself. The erased terrain returns a few seconds later.
 *  DONE             displays removed.
 * </pre>
 *
 * <p>This class holds the <b>when and what</b> (timing, hit-scan, damage); every particle, sound, display and
 * client-side erasure lives in {@link PurpleBuilder}. Nothing real in the world is ever changed.</p>
 *
 * <h3>Tuning</h3>
 * <p>Live keys: {@code abilities.techniques.purple.damage} (default 24.0),
 * {@code cooldown} and {@code armor-piercing}. All other values below are shipped constants.</p>
 * <ul>
 *   <li>{@code conjure-ticks} (14), {@code fuse-ticks} (12), {@code speed} (2.4, reached after {@code accel-ticks} = 10),
 *       {@code range} (60, plus 8 per power)</li>
 *   <li>{@code radius-base} (2.2), {@code radius-per-power} (0.35) — the width of the erased tube</li>
 *   <li>{@code remote-radius-base} (9), {@code remote-radius-per-power} (2.5), {@code remote-expand-ticks} (14)</li>
 *   <li>{@code erase-visual} (true), {@code erase-seconds} (6), {@code max-erase-blocks} (6000),
 *       {@code camera-shake} (true), {@code quality} (1.0, range 0.4–2.0)</li>
 * </ul>
 */
public class PurpleProjectile extends BukkitRunnable {
    /** Shipped per-power damage; live value comes from {@code abilities.techniques.purple.damage}. */
    public static final double DEFAULT_DAMAGE_PER_POWER = 24.0;

    /** Live per-power damage, swapped by {@code /bg reload}. */
    public static double configuredDamage() {
        return AbilityTuningConfig.current().damage("purple", DEFAULT_DAMAGE_PER_POWER);
    }

    /**
     * Resolves the damage that reaches a hit. The holder already clamps to the documented
     * 0..200 range, so this keeps the configured value verbatim — no legacy lower cap may
     * silently reduce a valid configuration.
     */
    public static double effectiveDamage(double configured) {
        return Math.max(0.0, configured);
    }

    /** Live armor-piercing toggle, swapped by {@code /bg reload}. */
    public static boolean armorPiercingEnabled() {
        return AbilityTuningConfig.current().armorPiercing("purple", true);
    }


    /** The technique's phases, in order. */
    public enum State {
        /** Blue at the left hand, Red at the right. */
        CONJURE,
        /** (Remote) the two are thrown apart toward the target. */
        SEND,
        /** The two collide and fuse. */
        FUSE,
        /** (Standard) the imaginary mass flies, erasing everything. */
        FIRE,
        /** (Remote) the fused point detonates into a sphere of erasure. */
        BLAST,
        /** The imaginary mass folds in on itself. */
        COLLAPSE,
        /** Finished. */
        DONE
    }
    /** Ticks the collapse lasts. */
    static final int COLLAPSE_TICKS = 14;
    /** Ticks the remote blast lingers at full size after it has expanded. */
    private static final int BLAST_LINGER_TICKS = 8;
    private static final int SHAKE_TICKS = 12;

    // ── Tunables (read once per cast) ──
    private final int conjureTicks;
    private final int fuseTicks;
    private final double maxSpeed;
    private final int accelTicks;
    private final double range;
    private final double tubeRadius;
    private final double damagePerPower;
    private final boolean armorPiercing;
    private final double nukeRadius;
    private final int expandTicks;
    private final boolean eraseVisual;
    private final int eraseTicks;
    private final int maxErase;
    private final boolean cameraShake;
    private final double quality;

    // ── Cast state ──
    private final Player caster;
    private final double power;
    private final boolean remote;
    private State state;
    private Vector direction;
    private Location currentLocation;
    private Location fusePoint;
    private Location target;
    private int ticksLived;
    private int stateTicks;
    private int sendTicks;
    private double traveled;
    private double speedNow;
    private int shakeLeft;
    private final Set<UUID> hit = new HashSet<>();
    private final Map<UUID, float[]> shakes = new HashMap<>();

    /** Tracks the windup root so release/cancel removes only what we granted. */
    private final WindupSlowness windupSlowness = new WindupSlowness();

    private PurpleBuilder.Rig rig;

    /**
     * @param caster the user
     * @param power  1.0–5.0 — scales path width, range, damage and the remote blast radius
     * @param remote true for "remote" Purple (Blue and Red collide at the crosshair)
     */
    public PurpleProjectile(Player caster, double power, boolean remote) {
        this.caster = caster;
        this.power = power;
        this.remote = remote;
        this.state = State.CONJURE;
        this.direction = caster.getLocation().getDirection().normalize();
        this.currentLocation = caster.getEyeLocation();

        boolean max = power >= 4.0;
        this.conjureTicks = clampInt(14, 4, 80);
        this.fuseTicks = clampInt(12, 4, 60);
        this.maxSpeed = clamp(2.4, 0.5, 5.0);
        this.accelTicks = clampInt(10, 1, 60);
        this.range = clamp(60.0, 10.0, 200.0) + 8.0 * power;
        double radius = clamp(2.2, 0.8, 8.0)
                + clamp(0.35, 0.0, 2.0) * power;
        this.tubeRadius = radius * (max ? 1.3 : 1.0);
        this.damagePerPower = effectiveDamage(configuredDamage());
        this.armorPiercing = armorPiercingEnabled();
        double nuke = clamp(9.0, 3.0, 40.0)
                + clamp(2.5, 0.0, 10.0) * power;
        this.nukeRadius = nuke * (max ? 1.4 : 1.0);
        this.expandTicks = clampInt(14, 4, 60);
        this.eraseVisual = true;
        this.eraseTicks = clampInt((int) Math.round(6.0 * 20.0), 20, 1200);
        this.maxErase = clampInt(6000, 100, 40000);
        this.cameraShake = true;
        this.quality = clamp(1.0, 0.4, 2.0);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Starts the state machine on the server tick loop. */
    public void start() {
        this.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    // ------------------------------------------------------------------
    // The loop
    // ------------------------------------------------------------------

    @Override
    public void run() {
        try {
            step();
        } catch (Throwable t) {
            ShenanigansCore.getInstance().getLogger().warning("Purple failed, cleaning up: " + t);
            clearWindupSlowness();
            PurpleBuilder.cleanup(this);
            cancel();
        }
    }

    private void step() {
        ticksLived++;
        stateTicks++;

        // A caster who logs off or dies before the fusion cancels the technique.
        if ((state == State.CONJURE || state == State.SEND || state == State.FUSE)
                && (!caster.isOnline() || caster.isDead() || caster.getWorld() != currentLocation.getWorld())) {
            clearWindupSlowness();
            PurpleBuilder.cleanup(this);
            cancel();
            return;
        }

        switch (state) {
            case CONJURE -> {
                if (stateTicks == 1) beginConjure();
                double progress = Math.min(1.0, stateTicks / (double) conjureTicks);
                PurpleBuilder.tickConjure(this, progress);
                if (stateTicks >= conjureTicks) {
                    if (remote) beginSend(); else beginFuse();
                }
            }
            case SEND -> {
                double progress = Math.min(1.0, stateTicks / (double) sendTicks);
                PurpleBuilder.tickSend(this, progress);
                if (stateTicks >= sendTicks) beginFuse();
            }
            case FUSE -> {
                double progress = Math.min(1.0, stateTicks / (double) fuseTicks);
                PurpleBuilder.tickFuse(this, progress);
                if (stateTicks >= fuseTicks) fusion();
            }
            case FIRE -> tickFire();
            case BLAST -> tickBlast();
            case COLLAPSE -> {
                if (stateTicks == 1) PurpleBuilder.beginCollapse(this);
                PurpleBuilder.tickCollapse(this, stateTicks);
                tickShake();
                if (stateTicks >= COLLAPSE_TICKS) enter(State.DONE);
            }
            case DONE -> {
                PurpleBuilder.cleanup(this);
                cancel();
            }
        }

        if (state != State.DONE) PurpleBuilder.tickAmbient(this);
    }

    private void enter(State next) {
        this.state = next;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------
    // CONJURE -> SEND -> FUSE
    // ------------------------------------------------------------------

    private void beginConjure() {
        PurpleBuilder.spawnHalves(this);
        // He plants his feet while both techniques are shaped at once.
        WindupSlownessCleanup.grant(caster, windupSlowness, conjureTicks + fuseTicks + 6, 3);
    }

    /** Removes the windup root only if this technique granted it (never a stranger's). */
    private void clearWindupSlowness() {
        WindupSlownessCleanup.clear(caster, windupSlowness);
    }

    /** (Remote) throws the two apart toward the crosshair. */
    private void beginSend() {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector aimDir = eye.getDirection().normalize();
        double max = Math.min(range, 45.0);

        RayTraceResult aim = world.rayTraceBlocks(eye, aimDir, max, FluidCollisionMode.NEVER, true);
        double dist = aim != null ? Math.max(12.0, eye.toVector().distance(aim.getHitPosition()) - 1.0) : max;
        target = eye.clone().add(aimDir.clone().multiply(Math.max(12.0, dist)));

        sendTicks = clampInt((int) Math.round(dist / 1.6), 8, 30);
        enter(State.SEND);
        PurpleBuilder.beginSend(this);
    }

    private void beginFuse() {
        Location eye = caster.getEyeLocation();
        fusePoint = remote ? target.clone() : eye.clone().add(eye.getDirection().normalize().multiply(2.6));
        enter(State.FUSE);
        PurpleBuilder.beginFuse(this);
    }

    /** The collision: Blue and Red become Purple. */
    private void fusion() {
        clearWindupSlowness();
        PurpleBuilder.fusion(this);
        registerShakes(fusePoint, 40.0, 3.0);

        caster.showTitle(Title.title(
                MiniMessage.miniMessage().deserialize("<light_purple><bold>茈"),
                MiniMessage.miniMessage().deserialize("<dark_purple>Hollow Technique: Purple"
                        + (power >= 4.0 ? " <gray>(maximum output)" : "")),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(300))));

        if (remote) {
            enter(State.BLAST);
        } else {
            currentLocation = fusePoint.clone();
            traveled = 0.0;
            speedNow = 0.0;
            enter(State.FIRE);
        }
    }

    // ------------------------------------------------------------------
    // FIRE (standard): the imaginary mass flies
    // ------------------------------------------------------------------

    private boolean canHit(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) return false;
        if (e.equals(caster) || e instanceof ArmorStand) return false;
        if (e instanceof Player p) {
            GameMode mode = p.getGameMode();
            return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
        }
        return true;
    }

    private void tickFire() {
        World world = currentLocation.getWorld();
        if (world == null) {
            enter(State.DONE);
            return;
        }

        if (stateTicks == 1) {
            // Lock the aim at the moment of firing: where the caster is looking now.
            Location eye = caster.getEyeLocation();
            Vector aimDir = eye.getDirection().normalize();
            RayTraceResult aim = world.rayTraceBlocks(eye, aimDir, range, FluidCollisionMode.NEVER, true);
            Location aimPoint = aim != null ? aim.getHitPosition().toLocation(world)
                    : eye.clone().add(aimDir.clone().multiply(range));
            Vector toAim = aimPoint.toVector().subtract(fusePoint.toVector());
            direction = toAim.lengthSquared() > 4.0 ? toAim.normalize() : aimDir;
            PurpleBuilder.beginFire(this);
            registerShakes(fusePoint, 40.0, 3.5);
        }

        // The mass starts slowly (it is still condensing) and accelerates to full speed.
        speedNow = Math.min(maxSpeed, 0.5 + stateTicks * (maxSpeed - 0.5) / accelTicks);
        Location prev = currentLocation.clone();
        currentLocation.add(direction.clone().multiply(speedNow));
        traveled += speedNow;

        PurpleBuilder.tickFire(this, prev, currentLocation, speedNow);
        damagePath(world, prev, currentLocation);
        tickShake();

        if (traveled >= range
                || !world.isChunkLoaded(currentLocation.getBlockX() >> 4, currentLocation.getBlockZ() >> 4)) {
            enter(State.COLLAPSE);
        }
    }

    /** Every living thing inside the tube is hit once: the imaginary mass ignores durability. */
    private void damagePath(World world, Location prev, Location cur) {
        double r = tubeRadius;
        Vector a = prev.toVector();
        Vector b = cur.toVector();
        Vector seg = b.clone().subtract(a);
        double reach = r + seg.length() / 2.0 + 2.0;
        Location mid = prev.clone().add(seg.clone().multiply(0.5));

        for (Entity e : world.getNearbyEntities(mid, reach, reach, reach)) {
            if (!canHit(e) || hit.contains(e.getUniqueId())) continue;
            LivingEntity victim = (LivingEntity) e;
            Vector chest = victim.getLocation().add(0, victim.getHeight() * 0.5, 0).toVector();
            double d = distanceToSegment(chest, a, b);
            if (d > r + victim.getWidth() / 2.0 + 0.3) continue;
            hit.add(victim.getUniqueId());
            hitEntity(victim, direction.clone().multiply(1.0));
        }
    }

    private static double distanceToSegment(Vector p, Vector a, Vector b) {
        Vector ab = b.clone().subtract(a);
        double len2 = ab.lengthSquared();
        if (len2 < 1.0e-9) return p.distance(a);
        double t = Math.max(0.0, Math.min(1.0, p.clone().subtract(a).dot(ab) / len2));
        return p.distance(a.clone().add(ab.multiply(t)));
    }

    // ------------------------------------------------------------------
    // BLAST (remote): a sphere of erasure
    // ------------------------------------------------------------------

    private void tickBlast() {
        World world = fusePoint.getWorld();
        if (world == null) {
            enter(State.DONE);
            return;
        }

        if (stateTicks == 1) {
            registerShakes(fusePoint, 60.0, 5.0);
            PurpleBuilder.beginBlast(this);
        }

        double f = Math.min(1.0, stateTicks / (double) expandTicks);
        double radius = nukeRadius * (1.0 - Math.pow(1.0 - f, 2.2));
        PurpleBuilder.tickBlast(this, stateTicks, radius);

        // Everything the front reaches is hit once, thrown outward.
        double reach = radius + 2.0;
        for (Entity e : world.getNearbyEntities(fusePoint, reach, reach, reach)) {
            if (!canHit(e) || hit.contains(e.getUniqueId())) continue;
            LivingEntity victim = (LivingEntity) e;
            Location chest = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
            double d = chest.distance(fusePoint);
            if (d > radius + victim.getWidth() / 2.0) continue;
            hit.add(victim.getUniqueId());
            Vector out = chest.toVector().subtract(fusePoint.toVector());
            out = out.lengthSquared() < 0.01 ? new Vector(0, 1, 0) : out.normalize();
            hitEntity(victim, out.multiply(1.4));
        }
        tickShake();

        if (stateTicks >= expandTicks + BLAST_LINGER_TICKS) enter(State.COLLAPSE);
    }

    // ------------------------------------------------------------------
    // Damage
    // ------------------------------------------------------------------

    /** One enormous, armor-piercing hit and a shove along {@code push}. */
    private void hitEntity(LivingEntity victim, Vector push) {
        double dmg = Math.max(2.0, damagePerPower * power);

        // Sure-hit through invulnerability frames.
        victim.setNoDamageTicks(0);
        dealDamage(victim, dmg);

        Vector kb = push.clone();
        if (kb.lengthSquared() > 1.0e-6) kb.normalize().multiply(0.9 + 0.2 * power);
        kb.setY(Math.max(kb.getY(), 0.0) + 0.3);
        if (kb.length() > 3.5) kb.normalize().multiply(3.5);
        victim.setVelocity(kb);

        if (victim instanceof Player pl) {
            pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<light_purple>Hollow Purple <dark_gray>| <white>-" + (int) Math.ceil(dmg / 2.0) + "❤"));
            registerShake(pl, 5.0);
        }
        PurpleBuilder.onHit(this, victim);
    }

    /** Armor-piercing damage (the mass ignores durability), with a safe fallback. */
    private void dealDamage(LivingEntity victim, double dmg) {
        if (armorPiercing) {
            try {
                DamageSource source = DamageSource.builder(DamageType.MAGIC)
                        .withCausingEntity(caster)
                        .withDirectEntity(caster)
                        .build();
                victim.damage(dmg, source);
                return;
            } catch (Throwable ignored) {
                // Fall back to ordinary damage on servers without the DamageSource API.
            }
        }
        victim.damage(dmg, caster);
    }

    // ------------------------------------------------------------------
    // Camera shake
    // ------------------------------------------------------------------

    private void registerShake(Player p, double amplitude) {
        if (!cameraShake) return;
        Location l = p.getLocation();
        shakes.put(p.getUniqueId(), new float[]{l.getYaw(), l.getPitch(), (float) Math.min(amplitude, 6.0)});
        shakeLeft = SHAKE_TICKS;
    }

    private void registerShakes(Location at, double radius, double amplitude) {
        if (!cameraShake || at == null || at.getWorld() == null) return;
        for (Player pl : at.getWorld().getPlayers()) {
            double d = pl.getLocation().distance(at);
            if (d <= radius) registerShake(pl, amplitude * (1.0 - d / radius) + 0.5);
        }
    }

    private void tickShake() {
        if (!cameraShake || shakeLeft <= 0 || shakes.isEmpty()) return;
        double decay = shakeLeft / (double) SHAKE_TICKS;
        shakeLeft--;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (Map.Entry<UUID, float[]> entry : shakes.entrySet()) {
            Player pl = Bukkit.getPlayer(entry.getKey());
            if (pl == null || !pl.isOnline()) continue;
            float[] s = entry.getValue();
            float amp = (float) (s[2] * decay);
            float yaw = s[0] + (rng.nextFloat() * 2f - 1f) * amp;
            float pitch = Math.max(-89f, Math.min(89f, s[1] + (rng.nextFloat() * 2f - 1f) * amp * 0.6f));
            pl.setRotation(yaw, pitch);
        }
        if (shakeLeft <= 0) shakes.clear();
    }

    // ------------------------------------------------------------------
    // Accessors for PurpleBuilder (same package)
    // ------------------------------------------------------------------

    /** The user. */
    public Player caster() {
        return caster;
    }

    /** 1.0–5.0. */
    public double power() {
        return power;
    }

    /** True for "remote" Purple. */
    public boolean isRemote() {
        return remote;
    }

    /** True for a "maximum output" Purple (power 4.0 and up). */
    public boolean isMax() {
        return power >= 4.0;
    }

    /** The current phase. */
    public State state() {
        return state;
    }

    /** The travel direction (valid from FIRE on). */
    public Vector direction() {
        return direction;
    }

    /** Where the imaginary mass is right now (FIRE). */
    public Location currentLocation() {
        return currentLocation;
    }

    /** Where Blue and Red collide (valid from FUSE on). */
    public Location fusePoint() {
        return fusePoint;
    }

    /** The remote target (valid from SEND on, remote only). */
    public Location target() {
        return target;
    }

    /** Ticks since the cast began. */
    public int ticksLived() {
        return ticksLived;
    }

    /** Ticks spent in the current phase (1 on the first tick). */
    public int stateTicks() {
        return stateTicks;
    }

    /** Width of the erased tube (standard) in blocks. */
    public double tubeRadius() {
        return tubeRadius;
    }

    /** Final radius of the remote sphere of erasure. */
    public double nukeRadius() {
        return nukeRadius;
    }

    /** Whether terrain is erased client-side. */
    public boolean eraseVisual() {
        return eraseVisual;
    }

    /** How long erased terrain stays gone, in ticks. */
    public int eraseTicks() {
        return eraseTicks;
    }

    /** Cap on how many blocks may be erased in one cast. */
    public int maxErase() {
        return maxErase;
    }

    /** Effect density multiplier (config quality, 0.4-2.0). */
    public double quality() {
        return quality;
    }

    PurpleBuilder.Rig rig() {
        return rig;
    }

    void setRig(PurpleBuilder.Rig rig) {
        this.rig = rig;
    }
}
