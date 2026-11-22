package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlowness;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlownessCleanup;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Cursed Technique Lapse: Blue — the point of attraction's state machine.
 *
 * <p><b>Design (from the lore):</b> Gojo powers Limitless with negative cursed energy, making
 * "negative distance" real: space is forced to fill the gap, so everything is dragged toward a
 * single point like a violent magnet. How strong the pull is depends on how much cursed energy is
 * poured in, and the point can be placed away from the user. So Blue here is a <b>placed gravity
 * well</b>, not a projectile that explodes — the mirror image of Red:</p>
 *
 * <pre>
 *  CHARGE    ~0.5s   a small blue point condenses at the raised hand.
 *  FLIGHT            it drifts out to where the caster is looking (or the first thing in the way).
 *  WELL      ~4–7s   it anchors and PULLS: every entity, item and arrow in range is dragged toward
 *                    the core in a spiral; anything that reaches the core is crushed. The caster is
 *                    never pulled. Terrain is torn up (visually) and ground into the core.
 *  COLLAPSE  ~1.1s   the pull doubles, everything is crushed in, then the space snaps back with a
 *                    gentle outward release so nobody is left stuck inside.
 *  DONE              displays removed; ground scars fade out on their own.
 * </pre>
 *
 * <p>This class holds the <b>when and what</b> (timing, pull, damage); every particle, sound and
 * display lives in {@link BlueBuilder}.</p>
 *
 * <h3>Tuning</h3>
 * <p>Live keys: {@code abilities.techniques.blue.damage} (default 2.0, applied every half second
 * inside the core) and {@code cooldown}. All other values below are shipped constants.</p>
 * <ul>
 *   <li>{@code charge-ticks} (10), {@code speed} (1.1, plus 0.1 per power), {@code range} (40),
 *       {@code max-flight-ticks} (80)</li>
 *   <li>{@code well-seconds} (4.0, plus 0.6 per power), {@code pull-radius-base} (7),
 *       {@code pull-radius-per-power} (2), {@code pull-strength} (0.16), {@code max-pull-speed} (2.0)</li>
 *   <li>{@code core-radius} (1.5, plus 0.1 per power), {@code release-push} (0.8)</li>
 *   <li>{@code affect-items} (true — dropped items, arrows and falling blocks are pulled too),
 *       {@code affect-caster} (false), {@code crater} (true — terrain is lifted visually),
 *       {@code scar-seconds} (5), {@code quality} (1.0, range 0.4–2.0)</li>
 * </ul>
 */
public class BlueProjectile extends BukkitRunnable {
    /** Shipped per-power damage; live value comes from {@code abilities.techniques.blue.damage}. */
    public static final double DEFAULT_DAMAGE_PER_POWER = 2.0;

    /** Live per-power damage, swapped by {@code /bg reload}. */
    public static double configuredDamage() {
        return AbilityTuningConfig.current().damage("blue", DEFAULT_DAMAGE_PER_POWER);
    }

    /**
     * Resolves the damage that reaches a hit. The holder already clamps to the documented
     * 0..200 range, so this keeps the configured value verbatim — no legacy lower cap may
     * silently reduce a valid configuration.
     */
    public static double effectiveDamage(double configured) {
        return Math.max(0.0, configured);
    }

    /** The technique's phases, in order. */
    public enum State {
        /** Condensing at the caster's hand. */
        CHARGE,
        /** Drifting out to where the caster looks. */
        FLIGHT,
        /** Anchored: pulling everything in. */
        WELL,
        /** Crushing in, then snapping back. */
        COLLAPSE,
        /** Finished. */
        DONE
    }
    /** Ticks the collapse lasts. */
    static final int COLLAPSE_TICKS = 22;
    /** Collapse tick on which the space snaps back (the release). */
    static final int RELEASE_TICK = 15;
    private static final int CRUSH_INTERVAL = 10;

    // ── Tunables (read once per cast) ──
    private final int chargeTicks;
    private final double speed;
    private final double range;
    private final int maxFlightTicks;
    private final int wellTicks;
    private final double pullRadius;
    private final double pullStrength;
    private final double maxPullSpeed;
    private final double coreRadius;
    private final double damagePerPower;
    private final double releasePush;
    private final boolean affectItems;
    private final boolean affectCaster;
    private final boolean crater;
    private final int scarTicks;
    private final double quality;

    // ── Cast state ──
    private final Player caster;
    private final double power;
    private Vector direction;
    private State state;

    /** Tracks the windup root so release/cancel removes only what we granted. */
    private final WindupSlowness windupSlowness = new WindupSlowness();
    private Location currentLocation;
    private double aimDistance;
    private int ticksLived;
    private int stateTicks;
    private int flightTicks;
    private double traveled;

    // ── Well state ──
    private Location center;

    private BlueBuilder.Rig rig;

    /**
     * @param caster the user
     * @param power  cursed energy poured in (1.0–5.0) — scales radius, pull, duration and damage
     */
    public BlueProjectile(Player caster, double power) {
        this.caster = caster;
        this.power = power;
        this.direction = caster.getLocation().getDirection().normalize();
        this.state = State.CHARGE;
        this.currentLocation = caster.getEyeLocation();

        boolean max = power >= 4.0;
        this.chargeTicks = clampInt(10, 4, 60);
        this.speed = MathUtil.clamp(1.1, 0.3, 3.0) + power * 0.1;
        this.range = MathUtil.clamp(40.0, 8.0, 150.0);
        this.maxFlightTicks = clampInt(80, 10, 300);
        double seconds = MathUtil.clamp(4.0, 1.0, 30.0) + 0.6 * power;
        this.wellTicks = (int) Math.round(seconds * 20.0);
        double radius = MathUtil.clamp(7.0, 2.0, 40.0)
                + MathUtil.clamp(2.0, 0.0, 10.0) * power;
        this.pullRadius = radius * (max ? 1.4 : 1.0);
        this.pullStrength = MathUtil.clamp(0.16, 0.02, 1.0) * (max ? 1.3 : 1.0);
        this.maxPullSpeed = MathUtil.clamp(2.0, 0.5, 4.0);
        this.coreRadius = MathUtil.clamp(1.5, 0.5, 6.0) + 0.1 * power;
        this.damagePerPower = effectiveDamage(configuredDamage());
        this.releasePush = MathUtil.clamp(0.8, 0.0, 3.0);
        this.affectItems = true;
        this.affectCaster = false;
        this.crater = true;
        this.scarTicks = clampInt((int) Math.round(5.0 * 20.0), 20, 1200);
        this.quality = MathUtil.clamp(1.0, 0.4, 2.0);
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
            ShenanigansCore.getInstance().getLogger().warning("Blue failed, cleaning up: " + t);
            clearWindupSlowness();
            BlueBuilder.cleanup(this);
            cancel();
        }
    }

    private void step() {
        ticksLived++;
        stateTicks++;

        // A caster who logs off or dies before the point is placed cancels it.
        if ((state == State.CHARGE || state == State.FLIGHT)
                && (!caster.isOnline() || caster.isDead() || caster.getWorld() != currentLocation.getWorld())) {
            clearWindupSlowness();
            BlueBuilder.cleanup(this);
            cancel();
            return;
        }

        switch (state) {
            case CHARGE -> {
                if (stateTicks == 1) beginCharge();
                double progress = Math.min(1.0, stateTicks / (double) chargeTicks);
                direction = caster.getEyeLocation().getDirection().normalize();
                BlueBuilder.tickCharge(this, progress);
                if (stateTicks >= chargeTicks) release();
            }
            case FLIGHT -> tickFlight();
            case WELL -> {
                if (stateTicks == 1) BlueBuilder.beginWell(this);
                pull(1.0, stateTicks);
                BlueBuilder.tickWell(this, stateTicks);
                if (stateTicks % 5 == 0 && caster.isOnline()) {
                    double left = Math.max(0.0, (wellTicks - stateTicks) / 20.0);
                    caster.sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<blue>Lapse: Blue <dark_gray>| <white>" + String.format("%.1f", left) + "s"));
                }
                if (stateTicks >= wellTicks) enter(State.COLLAPSE);
            }
            case COLLAPSE -> {
                if (stateTicks == 1) BlueBuilder.beginCollapse(this);
                // The pull doubles while everything is crushed in, then lets go at the release tick.
                if (stateTicks < RELEASE_TICK) pull(2.0, wellTicks + stateTicks);
                BlueBuilder.tickCollapse(this, stateTicks);
                if (stateTicks == RELEASE_TICK) applyRelease();
                if (stateTicks >= COLLAPSE_TICKS) enter(State.DONE);
            }
            case DONE -> {
                BlueBuilder.cleanup(this);
                cancel();
            }
        }

        if (state != State.DONE) BlueBuilder.tickAmbient(this);
    }

    private void enter(State next) {
        this.state = next;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------
    // CHARGE -> FLIGHT
    // ------------------------------------------------------------------

    private void beginCharge() {
        BlueBuilder.spawnOrb(this);
        // He plants his feet while the cursed energy is shaped.
        WindupSlownessCleanup.grant(caster, windupSlowness, chargeTicks + 2, 2);
        caster.sendActionBar(MiniMessage.miniMessage().deserialize("<blue>Lapse <gray>...converging"));
    }

    /** Removes the windup root only if this technique granted it (never a stranger's). */
    private void clearWindupSlowness() {
        WindupSlownessCleanup.clear(caster, windupSlowness);
    }

    /** Sends the point out toward the crosshair. */
    private void release() {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector aimDir = eye.getDirection().normalize();

        Location muzzle = rig == null ? null : BlueBuilder.centerOf(rig);
        if (muzzle == null) muzzle = eye.clone().add(aimDir.clone().multiply(1.7));

        // Fly to the crosshair: the first thing in the way, or the end of the range.
        RayTraceResult aim = world.rayTrace(eye, aimDir, range, FluidCollisionMode.NEVER, true, 0.5, this::canHit);
        Location aimPoint = aim != null
                ? aim.getHitPosition().toLocation(world)
                : eye.clone().add(aimDir.clone().multiply(range));
        Vector toAim = aimPoint.toVector().subtract(muzzle.toVector());
        direction = toAim.lengthSquared() > 4.0 ? toAim.normalize() : aimDir;
        aimDistance = Math.max(3.0, Math.min(range, toAim.length()));

        currentLocation = muzzle.clone();
        clearWindupSlowness();

        caster.showTitle(Title.title(
                MiniMessage.miniMessage().deserialize("<aqua><bold>蒼"),
                MiniMessage.miniMessage().deserialize("<blue>Lapse: Blue"
                        + (power >= 4.0 ? " <gray>(maximum output)" : "")),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(450), Duration.ofMillis(250))));

        flightTicks = 0;
        traveled = 0.0;
        enter(State.FLIGHT);
        BlueBuilder.release(this);
    }

    // ------------------------------------------------------------------
    // FLIGHT
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

    private void tickFlight() {
        flightTicks++;
        World world = currentLocation.getWorld();
        if (world == null) {
            enter(State.DONE);
            return;
        }

        Location prev = currentLocation.clone();
        RayTraceResult hit = world.rayTrace(prev, direction, speed,
                FluidCollisionMode.NEVER, true, 0.6, this::canHit);

        if (hit != null) {
            Vector hp = hit.getHitPosition();
            Location at = new Location(world, hp.getX(), hp.getY(), hp.getZ());
            if (hit.getHitEntity() instanceof LivingEntity victim) {
                at = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
            } else if (hit.getHitBlockFace() != null) {
                // Hold the point just off the surface so it hangs in open air.
                at.add(hit.getHitBlockFace().getDirection().multiply(1.1));
            }
            currentLocation = at;
            BlueBuilder.tickFlight(this, prev, currentLocation);
            anchor();
            return;
        }

        currentLocation.add(direction.clone().multiply(speed));
        traveled += speed;
        BlueBuilder.tickFlight(this, prev, currentLocation);

        if (traveled >= aimDistance - 0.2 || flightTicks > maxFlightTicks
                || !world.isChunkLoaded(currentLocation.getBlockX() >> 4, currentLocation.getBlockZ() >> 4)) {
            anchor();
        }
    }

    /** Plants the well at the current point (pulled out of any solid block). */
    private void anchor() {
        Location at = currentLocation.clone();
        if (at.getBlock().getType().isSolid()) {
            at.subtract(direction.clone().multiply(1.2));
        }
        center = at;
        enter(State.WELL);
    }

    // ------------------------------------------------------------------
    // WELL: the pull
    // ------------------------------------------------------------------

    private boolean pullable(Entity e) {
        if (e instanceof Display || e instanceof Interaction || e instanceof Marker || e instanceof ArmorStand) return false;
        if (e.equals(caster)) return affectCaster;
        if (e instanceof Player p) {
            GameMode mode = p.getGameMode();
            return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR && p.isValid() && !p.isDead();
        }
        if (e instanceof LivingEntity le) return le.isValid() && !le.isDead();
        return affectItems && (e instanceof Item || e instanceof Projectile || e instanceof FallingBlock);
    }

    /**
     * Drags everything in range toward the core in a spiral. Entities that reach the core are crushed.
     * {@code mult} scales the pull (2.0 during the collapse).
     */
    private void pull(double mult, int clock) {
        if (center == null) return;
        World world = center.getWorld();
        if (world == null) return;

        double r = pullRadius;
        boolean crush = clock % CRUSH_INTERVAL == 0;
        int processed = 0;

        for (Entity e : world.getNearbyEntities(center, r, r, r)) {
            if (++processed > 80) break;
            if (!pullable(e)) continue;

            boolean living = e instanceof LivingEntity;
            Location pos = living ? ((LivingEntity) e).getLocation().add(0, ((LivingEntity) e).getHeight() * 0.5, 0)
                    : e.getLocation();
            Vector to = center.toVector().subtract(pos.toVector());
            double dist = to.length();
            if (dist > r || dist < 0.05) continue;
            to.multiply(1.0 / dist);

            double falloff = Math.pow(1.0 - dist / r, 1.3);
            double s = pullStrength * (0.25 + falloff) * mult * (living ? 1.0 : 1.4);

            // A sideways component turns the straight pull into a spiral.
            Vector tangent = to.clone().crossProduct(new Vector(0, 1, 0)).multiply(0.35 * s);
            Vector v = e.getVelocity().multiply(0.84).add(to.clone().multiply(s)).add(tangent);
            if (v.length() > maxPullSpeed) v.normalize().multiply(maxPullSpeed);
            e.setVelocity(v);
            e.setFallDistance(0f);

            // The core crushes whatever reaches it.
            if (living && crush && dist <= coreRadius && !e.equals(caster)) {
                LivingEntity victim = (LivingEntity) e;
                double dmg = Math.max(1.0, damagePerPower * power);
                victim.setNoDamageTicks(0);
                victim.damage(dmg, caster);
                if (victim instanceof Player pl) {
                    pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                            "<blue>Lapse: Blue <dark_gray>| <white>-" + (int) Math.ceil(dmg / 2.0) + "❤"));
                }
            }
        }
    }

    /** The space snaps back: a gentle outward push so nobody is left stuck in the core. */
    private void applyRelease() {
        if (center == null || releasePush <= 0.0) return;
        World world = center.getWorld();
        if (world == null) return;
        double r = pullRadius * 0.7;

        for (Entity e : world.getNearbyEntities(center, r, r, r)) {
            if (!(e instanceof LivingEntity) && !(affectItems && e instanceof Item)) continue;
            if (e instanceof Display || e instanceof ArmorStand || e.equals(caster)) continue;
            if (e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) continue;

            Location pos = e.getLocation().add(0, 0.5, 0);
            Vector out = pos.toVector().subtract(center.toVector());
            double dist = out.length();
            out = dist < 0.05 ? new Vector(0, 1, 0) : out.multiply(1.0 / dist);
            double f = 1.0 - Math.min(1.0, dist / r);
            Vector v = out.multiply(releasePush * (0.4 + f));
            v.setY(Math.max(v.getY(), 0.0) + 0.25);
            e.setVelocity(v);
        }
    }

    // ------------------------------------------------------------------
    // Accessors for BlueBuilder (same package)
    // ------------------------------------------------------------------

    /** The user. */
    public Player caster() {
        return caster;
    }

    /** Cursed energy poured in (1.0–5.0). */
    public double power() {
        return power;
    }

    /** True for a "maximum output" Blue (power 4.0 and up). */
    public boolean isMax() {
        return power >= 4.0;
    }

    /** Where the point is right now (before it anchors). */
    public Location currentLocation() {
        return currentLocation;
    }

    /** The normalised travel direction (follows the caster's aim until release). */
    public Vector direction() {
        return direction;
    }

    /** Where the well is anchored (valid from WELL on). */
    public Location center() {
        return center;
    }

    /** The current phase. */
    public State state() {
        return state;
    }

    /** Ticks since the cast began. */
    public int ticksLived() {
        return ticksLived;
    }

    /** Ticks spent in the current phase (1 on the first tick). */
    public int stateTicks() {
        return stateTicks;
    }

    /** Length of the charge in ticks. */
    public int chargeTicks() {
        return chargeTicks;
    }

    /** Ticks the point has been flying. */
    public int flightTicks() {
        return flightTicks;
    }

    /** Length of the well in ticks. */
    public int wellTicks() {
        return wellTicks;
    }

    /** Radius within which things are pulled. */
    public double pullRadius() {
        return pullRadius;
    }

    /** Radius within which things are crushed. */
    public double coreRadius() {
        return coreRadius;
    }

    /** Whether terrain is lifted visually. */
    public boolean crater() {
        return crater;
    }

    /** How long the ground scars linger, in ticks. */
    public int scarTicks() {
        return scarTicks;
    }

    /** Effect density multiplier (config quality, 0.4-2.0). */
    public double quality() {
        return quality;
    }

    BlueBuilder.Rig rig() {
        return rig;
    }

    void setRig(BlueBuilder.Rig rig) {
        this.rig = rig;
    }
}
