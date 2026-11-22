package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlowness;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlownessCleanup;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Cursed Technique Reversal: Red — the orb's state machine.
 *
 * <p><b>Design (from the lore):</b> Red is the mirror of Blue. Where Blue pulls everything
 * inward, Red pours positive energy into Limitless and forces space to <i>diverge</i>:
 * a repulsive blast that hurls targets away. So Red is built around <b>displacement</b>,
 * not burning: it deals real damage, but its signature is the shockwave that throws
 * things — and the ground — outward.</p>
 *
 * <pre>
 *  CHARGE  ~0.9s   A crimson orb condenses at the raised hand. Air is pushed away from it.
 *  FLIGHT          The orb is thrown fast. It shoves aside anything that brushes past it
 *                  and is ray-traced every tick (no tunnelling through walls or targets).
 *  IMPACT  ~2.5s   The orb collapses for three ticks, then diverges: a radial blast flings
 *                  every victim outward, tears the ground up, cracks the earth.
 *  DONE            displays removed; the fault-line cracks fade out on their own.
 * </pre>
 *
 * <p>This class holds the <b>when and what</b> (timing, collision, damage, knockback);
 * every particle, sound and display lives in {@link Builder}.</p>
 *
 * <h3>Tuning</h3>
 * <p>Live keys: {@code abilities.techniques.red.damage} (default 18.0) and {@code cooldown}.
 * All other values below are shipped constants.</p>
 * <ul>
 *   <li>{@code charge-ticks} (18), {@code speed} (2.2, plus 0.2 per power), {@code max-flight-ticks} (100),
 *       {@code max-range} (180)</li>
 *   <li>{@code knockback} (2.0), {@code radius-base} (5.5),
 *       {@code radius-per-power} (2.2)</li>
 *   <li>{@code crater} (true — real terrain is flung out visually), {@code camera-shake} (true),
 *       {@code scar-seconds} (7), {@code quality} (1.0, range 0.4–2.0)</li>
 * </ul>
 */
public class Projectile extends BukkitRunnable {
    /** Shipped per-power damage; live value comes from {@code abilities.techniques.red.damage}. */
    public static final double DEFAULT_DAMAGE_PER_POWER = 18.0;

    /** Live per-power damage, swapped by {@code /bg reload}. */
    public static double configuredDamage() {
        return AbilityTuningConfig.current().damage("red", DEFAULT_DAMAGE_PER_POWER);
    }

    /**
     * Resolves the damage that reaches a hit. The holder already clamps to the documented
     * 0..200 range, so this keeps the configured value verbatim — no legacy lower cap may
     * silently reduce a valid configuration.
     */
    public static double effectiveDamage(double configured) {
        return Math.max(0.0, configured);
    }

    /** The orb's phases, in order. */
    public enum State {
        /** Condensing at the caster's hand. */
        CHARGE,
        /** Travelling through the world. */
        FLIGHT,
        /** Collapsing, then diverging. */
        IMPACT,
        /** Finished. */
        DONE
    }
    private static final int IMPACT_TICKS = 50;
    /** Ticks of "collapse" before the blast (the anticipation beat). */
    private static final int COMPRESS_TICKS = 3;
    private static final int SHAKE_TICKS = 14;

    // ── Tunables (read once per cast) ──
    private final int chargeTicks;
    private final double speed;
    private final int maxFlightTicks;
    private final double maxRange;
    private final double damagePerPower;
    private final double knockback;
    private final double radiusBase;
    private final double radiusPerPower;
    private final boolean crater;
    private final boolean cameraShake;
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
    private int ticksLived;
    private int stateTicks;
    private int flightTicks;
    private double traveled;

    // ── Impact state ──
    private Location impactLocation;
    private double impactRadius;
    private LivingEntity hitEntity;
    private final Map<UUID, float[]> shakes = new HashMap<>();
    private final Map<UUID, Integer> lastPush = new HashMap<>();

    private Builder.Rig rig;

    /**
     * @param caster the thrower
     * @param power  the orb's power (1.0–5.0) — scales speed, radius and damage
     */
    public Projectile(Player caster, double power) {
        this.caster = caster;
        this.power = power;
        this.direction = caster.getLocation().getDirection().normalize();
        this.state = State.CHARGE;
        this.currentLocation = caster.getEyeLocation();

        this.chargeTicks = clampInt(18, 4, 60);
        this.speed = MathUtil.clamp(2.2, 0.5, 4.0) + power * 0.2;
        this.maxFlightTicks = clampInt(100, 10, 300);
        this.maxRange = MathUtil.clamp(180.0, 20.0, 300.0);
        this.damagePerPower = effectiveDamage(configuredDamage());
        this.knockback = MathUtil.clamp(2.0, 0.0, 4.0);
        this.radiusBase = MathUtil.clamp(5.5, 1.0, 20.0);
        this.radiusPerPower = MathUtil.clamp(2.2, 0.0, 6.0);
        this.crater = true;
        this.cameraShake = true;
        this.scarTicks = clampInt((int) Math.round(7.0 * 20.0), 20, 1200);
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
            ShenanigansCore.getInstance().getLogger().warning("Red failed, cleaning up: " + t);
            clearWindupSlowness();
            Builder.cleanup(this);
            cancel();
        }
    }

    private void step() {
        ticksLived++;
        stateTicks++;

        // A caster who logs off, dies or changes world cancels the orb before it flies.
        if ((state == State.CHARGE || state == State.FLIGHT)
                && (!caster.isOnline() || caster.isDead() || caster.getWorld() != currentLocation.getWorld())) {
            clearWindupSlowness();
            Builder.cleanup(this);
            cancel();
            return;
        }

        switch (state) {
            case CHARGE -> {
                if (stateTicks == 1) beginCharge();
                double progress = Math.min(1.0, stateTicks / (double) chargeTicks);
                direction = caster.getEyeLocation().getDirection().normalize();
                Builder.tickCharge(this, progress);
                if (stateTicks >= chargeTicks) release();
            }
            case FLIGHT -> tickFlight();
            case IMPACT -> {
                if (stateTicks <= COMPRESS_TICKS) {
                    Builder.tickCompress(this, stateTicks);
                } else {
                    int blast = stateTicks - COMPRESS_TICKS;
                    if (blast == 1) {
                        Builder.createBlast(this);
                        applyBlast();
                    }
                    Builder.tickBlast(this, blast);
                    tickShake(blast);
                }
                if (stateTicks >= IMPACT_TICKS) enter(State.DONE);
            }
            case DONE -> {
                Builder.cleanup(this);
                cancel();
            }
        }

        if (state != State.DONE) Builder.tickAmbient(this);
    }

    private void enter(State next) {
        this.state = next;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------
    // CHARGE -> FLIGHT
    // ------------------------------------------------------------------

    private void beginCharge() {
        Builder.spawnOrb(this);
        // He plants his feet while the orb condenses.
        WindupSlownessCleanup.grant(caster, windupSlowness, chargeTicks + 2, 2);
        caster.sendActionBar(MiniMessage.miniMessage().deserialize("<dark_red>Reversal <gray>...condensing"));
    }

    /** Removes the windup root only if this technique granted it (never a stranger's). */
    private void clearWindupSlowness() {
        WindupSlownessCleanup.clear(caster, windupSlowness);
    }

    /** Throws the orb: converge on the crosshair so it hits where the caster looks. */
    private void release() {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector aimDir = eye.getDirection().normalize();

        Location muzzle = rig == null ? null : Builder.centerOf(rig);
        if (muzzle == null) muzzle = eye.clone().add(aimDir.clone().multiply(1.7));

        RayTraceResult aim = world.rayTraceBlocks(eye, aimDir, 80.0, FluidCollisionMode.NEVER, true);
        Location aimPoint = aim != null
                ? aim.getHitPosition().toLocation(world)
                : eye.clone().add(aimDir.clone().multiply(80.0));
        Vector toAim = aimPoint.toVector().subtract(muzzle.toVector());
        direction = toAim.lengthSquared() > 9.0 ? toAim.normalize() : aimDir;

        currentLocation = muzzle.clone();
        clearWindupSlowness();

        // A little recoil, like throwing a weight.
        Vector recoil = direction.clone().multiply(-0.22 * Math.min(power, 3.0));
        recoil.setY(0.05);
        caster.setVelocity(caster.getVelocity().add(recoil));

        caster.showTitle(Title.title(
                MiniMessage.miniMessage().deserialize("<red><bold>赫"),
                MiniMessage.miniMessage().deserialize("<dark_red>Reversal: Red"
                        + (power >= 4.0 ? " <gray>(max output)" : "")),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(450), Duration.ofMillis(250))));

        flightTicks = 0;
        traveled = 0.0;
        enter(State.FLIGHT);
        Builder.release(this);

        // Thrown from inside a wall: it simply goes off on the spot.
        if (muzzle.getBlock().getType().isSolid()) {
            currentLocation = eye.clone();
            beginImpact();
        }
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
                FluidCollisionMode.NEVER, true, 0.8, this::canHit);

        if (hit != null) {
            Vector hp = hit.getHitPosition();
            Location at = new Location(world, hp.getX(), hp.getY(), hp.getZ());
            if (hit.getHitEntity() instanceof LivingEntity victim) {
                hitEntity = victim;
                at = victim.getLocation().add(0, victim.getHeight() * 0.5, 0);
            } else if (hit.getHitBlockFace() != null) {
                // Stand the blast just off the surface so it diverges in open air.
                at.add(hit.getHitBlockFace().getDirection().multiply(0.4));
            }
            currentLocation = at;
            Builder.tickFlight(this, prev, currentLocation);
            beginImpact();
            return;
        }

        currentLocation.add(direction.clone().multiply(speed));
        traveled += speed;
        Builder.tickFlight(this, prev, currentLocation);
        repelWake(world);

        if (flightTicks > maxFlightTicks || traveled > maxRange
                || !world.isChunkLoaded(currentLocation.getBlockX() >> 4, currentLocation.getBlockZ() >> 4)) {
            beginImpact();
        }
    }

    /**
     * Red repels. Anything that brushes past the orb — beside or behind it — is shoved aside.
     * Entities directly ahead are left alone so the ray-trace can still hit them.
     */
    private void repelWake(World world) {
        for (Entity raw : world.getNearbyEntities(currentLocation, 2.2, 2.2, 2.2)) {
            if (!canHit(raw)) continue;
            LivingEntity e = (LivingEntity) raw;
            Vector rel = e.getLocation().add(0, e.getHeight() * 0.5, 0).toVector()
                    .subtract(currentLocation.toVector());
            if (rel.lengthSquared() < 0.01) continue;
            if (rel.clone().normalize().dot(direction) > 0.2) continue;

            Integer last = lastPush.get(e.getUniqueId());
            if (last != null && ticksLived - last < 8) continue;
            lastPush.put(e.getUniqueId(), ticksLived);

            Vector push = rel.setY(0);
            if (push.lengthSquared() < 0.01) push = direction.clone().crossProduct(new Vector(0, 1, 0));
            push.normalize().multiply(0.55);
            push.setY(0.15);
            e.setVelocity(e.getVelocity().add(push));
        }
    }

    // ------------------------------------------------------------------
    // IMPACT
    // ------------------------------------------------------------------

    private void beginImpact() {
        impactLocation = currentLocation.clone();
        impactRadius = radiusBase + radiusPerPower * power;
        enter(State.IMPACT);
    }

    /** The divergence: damage, then every victim is thrown radially outward. */
    private void applyBlast() {
        World world = impactLocation.getWorld();
        if (world == null) return;
        double r = impactRadius;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (Entity raw : world.getNearbyEntities(impactLocation, r, r, r)) {
            if (!(raw instanceof LivingEntity victim)) continue;
            if (victim.equals(caster) || victim instanceof ArmorStand || !victim.isValid() || victim.isDead()) continue;
            if (victim instanceof Player pl
                    && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) continue;

            Location chest = victim.getLocation().add(0, victim.getHeight() * 0.55, 0);
            double dist = chest.distance(impactLocation);
            if (dist > r) continue;

            double falloff = 1.0 - 0.6 * (dist / r);
            Vector away = chest.toVector().subtract(impactLocation.toVector());

            // Cover: a solid wall between the blast and the victim blunts it.
            if (dist > 1.0) {
                RayTraceResult cover = world.rayTraceBlocks(impactLocation, away.clone().normalize(),
                        dist, FluidCollisionMode.NEVER, true);
                if (cover != null) falloff *= 0.4;
            }

            double dmg = damagePerPower * power * falloff;
            if (victim.equals(hitEntity)) dmg *= 1.3;
            dmg = Math.max(1.0, dmg);

            // Sure-hit through invulnerability frames.
            victim.setNoDamageTicks(0);
            victim.damage(dmg, caster);

            // Radial repulsion: the whole point of Red.
            Vector kb = away.lengthSquared() < 0.01 ? new Vector(0, 1, 0) : away.normalize();
            kb.multiply(knockback * (0.6 + 0.25 * power) * falloff);
            kb.setY(Math.max(kb.getY(), 0.0) + 0.45 * falloff + 0.1);
            if (kb.length() > 3.5) kb.normalize().multiply(3.5);
            victim.setVelocity(kb);

            if (victim instanceof Player pl) {
                pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<red>Reversal: Red <dark_gray>| <white>-" + (int) Math.ceil(dmg / 2.0) + "❤"));
                registerShake(pl, 4.0 * falloff + 1.0);
            }
            if (rng.nextInt(3) == 0) {
                SoundUtils.play(chest, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.0f, 0.7f + rng.nextFloat() * 0.4f);
            }
        }

        // Bystanders feel the ground-shake too.
        if (cameraShake) {
            for (Player pl : world.getPlayers()) {
                if (pl.equals(caster) || shakes.containsKey(pl.getUniqueId())) continue;
                double d = pl.getLocation().distance(impactLocation);
                if (d <= r * 2.2) registerShake(pl, 3.0 * (1.0 - d / (r * 2.2)) + 0.5);
            }
        }
    }

    private void registerShake(Player p, double amplitude) {
        if (!cameraShake) return;
        Location l = p.getLocation();
        shakes.put(p.getUniqueId(), new float[]{l.getYaw(), l.getPitch(), (float) Math.min(amplitude, 6.0)});
    }

    private void tickShake(int blastTick) {
        if (!cameraShake || shakes.isEmpty() || blastTick > SHAKE_TICKS) return;
        double decay = 1.0 - (blastTick / (double) SHAKE_TICKS);
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
    }

    // ------------------------------------------------------------------
    // Accessors for Builder (same package)
    // ------------------------------------------------------------------

    /** The thrower. */
    public Player caster() {
        return caster;
    }

    /** The orb's power (1.0–5.0). */
    public double power() {
        return power;
    }

    /** Where the orb is right now. */
    public Location currentLocation() {
        return currentLocation;
    }

    /** The normalised travel direction (follows the caster's aim until release). */
    public Vector direction() {
        return direction;
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

    /** Ticks the orb has been flying. */
    public int flightTicks() {
        return flightTicks;
    }

    /** Where the orb diverged (valid from IMPACT on). */
    public Location impactLocation() {
        return impactLocation;
    }

    /** Blast radius in blocks (valid from IMPACT on). */
    public double impactRadius() {
        return impactRadius;
    }

    /** The living thing the orb struck directly, or null. */
    public LivingEntity hitEntity() {
        return hitEntity;
    }

    /** True for a "max output" Red (power 4.0 and up). */
    public boolean isMax() {
        return power >= 4.0;
    }

    /** Whether real terrain is flung out visually. */
    public boolean crater() {
        return crater;
    }

    /** How long the ground cracks linger, in ticks. */
    public int scarTicks() {
        return scarTicks;
    }

    /** Effect density multiplier (config quality, 0.4-2.0). */
    public double quality() {
        return quality;
    }

    Builder.Rig rig() {
        return rig;
    }

    void setRig(Builder.Rig rig) {
        this.rig = rig;
    }
}
