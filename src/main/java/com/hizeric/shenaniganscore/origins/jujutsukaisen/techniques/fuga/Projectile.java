package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Session;
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
 * Fuga — the Fire Arrow's state machine.
 *
 * <pre>
 *  WINDUP  ~1.4s Sukuna draws a bow of flame. The arrow assembles on the string, embers
 *                spiral into it and a fire sigil burns under his feet. He cannot move.
 *  FLIGHT        "Open." The arrow is released toward the crosshair (it converges on the
 *                aim point, so it hits where you look even though it leaves from the
 *                bow, not the eye). Ray-traced every tick, so it can never tunnel
 *                through a wall or a fast target.
 *  IMPACT  ~2.1s the arrow detonates: white-hot core, rolling fire, shockwave, fire
 *                pillar, tumbling debris, scorched ground. Damage and knockback are
 *                applied on the first tick.
 *  DONE          every display is removed (the scorch mark fades out on its own).
 * </pre>
 *
 * <p><b>Malevolent Shrine enhancement.</b> If the caster owns a live Malevolent Shrine when
 * the arrow is drawn (look) or detonates (damage), Fuga is the domain's finisher:
 * a bigger bow and arrow, a doubled blast radius, more damage, the shrine's four mouths
 * lash into the impact point, the prepared ground is cut to pieces and erupts in
 * rippling fire spires.</p>
 *
 * <p>This class holds the <b>when and what</b> (timing, collision, damage); every particle,
 * sound and display lives in {@link Builder}.</p>
 *
 * <h3>Tuning</h3>
 * <p>Live keys: {@code abilities.techniques.fuga.damage} (default 24.0) and {@code cooldown}.
 * All other values below are shipped constants.</p>
 * <ul>
 *   <li>{@code windup-ticks} (28), {@code speed} (1.6, plus 0.25 per power),
 *       {@code max-flight-ticks} (130), {@code max-range} (240)</li>
 *   <li>{@code radius-base} (6.0), {@code radius-per-power} (2.5)</li>
 *   <li>{@code domain-radius-multiplier} (2.2), {@code domain-damage-multiplier} (1.75)</li>
 *   <li>{@code camera-shake} (true), {@code scorch-seconds} (8), {@code quality} (1.0, range 0.4-2.0)</li>
 * </ul>
 */
public class Projectile extends BukkitRunnable {
    /** Shipped per-power damage; live value comes from {@code abilities.techniques.fuga.damage}. */
    public static final double DEFAULT_DAMAGE_PER_POWER = 24.0;

    /** Live per-power damage, swapped by {@code /bg reload}. */
    public static double configuredDamage() {
        return AbilityTuningConfig.current().damage("fuga", DEFAULT_DAMAGE_PER_POWER);
    }

    /**
     * Resolves the damage that reaches a hit. The holder already clamps to the documented
     * 0..200 range, so this keeps the configured value verbatim — no legacy lower cap may
     * silently reduce a valid configuration.
     */
    public static double effectiveDamage(double configured) {
        return Math.max(0.0, configured);
    }

    /** The arrow's phases, in order. */
    public enum State {
        /** Drawing the bow at the caster's hand. */
        WINDUP,
        /** Travelling through the world. */
        FLIGHT,
        /** Detonating. */
        IMPACT,
        /** Finished. */
        DONE
    }
    private static final int IMPACT_TICKS = 42;
    private static final int IMPACT_TICKS_DOMAIN = 58;
    private static final int SHAKE_TICKS = 14;

    // ── Tunables (read once per cast) ──
    private final int windupTicks;
    private final double speed;
    private final int maxFlightTicks;
    private final double maxRange;
    private final double damagePerPower;
    private final double radiusBase;
    private final double radiusPerPower;
    private final double domainRadiusMultiplier;
    private final double domainDamageMultiplier;
    private final boolean cameraShake;
    private final int scorchTicks;
    private final double quality;

    // ── Cast state ──
    private final Player caster;
    private final double power;
    private final Location startLocation;
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
    private boolean domainBoost;
    private Session shrine;
    private Location impactLocation;
    private double impactRadius;
    private LivingEntity hitEntity;
    private final Map<UUID, float[]> shakes = new HashMap<>();

    private Builder.Rig rig;

    /**
     * @param caster the shooter
     * @param power  the arrow's power (1.0–5.0) — scales speed, radius and damage
     */
    public Projectile(Player caster, double power) {
        this.caster = caster;
        this.power = power;
        this.startLocation = caster.getEyeLocation();
        this.direction = caster.getLocation().getDirection().normalize();
        this.state = State.WINDUP;
        this.currentLocation = startLocation.clone();
        this.ticksLived = 0;

        this.windupTicks = clampInt(28, 6, 80);
        this.speed = MathUtil.clamp(1.6, 0.5, 3.5) + power * 0.25;
        this.maxFlightTicks = clampInt(130, 10, 400);
        this.maxRange = MathUtil.clamp(240.0, 20.0, 400.0);
        this.damagePerPower = effectiveDamage(configuredDamage());
        this.radiusBase = MathUtil.clamp(6.0, 1.0, 20.0);
        this.radiusPerPower = MathUtil.clamp(2.5, 0.0, 6.0);
        this.domainRadiusMultiplier = MathUtil.clamp(2.2, 1.0, 4.0);
        this.domainDamageMultiplier = MathUtil.clamp(1.75, 1.0, 4.0);
        this.cameraShake = true;
        this.quality = MathUtil.clamp(1.0, 0.4, 2.0);
        this.scorchTicks = clampInt((int) Math.round(8.0 * 20.0), 20, 1200);
    }

    private static int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    /** Starts the state machine on the server tick loop. */
    public void start() {
        this.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    /** The caster's own live Malevolent Shrine, or null. */
    static Session findShrine(Player caster) {
        try {
            for (DomainSession s : ShenanigansCore.getInstance().getSessionManager().getActiveSessions().values()) {
                if (s instanceof Session shrine && shrine.isActive()
                        && shrine.casterPlayer().getUniqueId().equals(caster.getUniqueId())
                        && shrine.getDomainElapsed() > 0.0) {
                    return shrine;
                }
            }
        } catch (Throwable ignored) {
            // No shrine info is never fatal: the arrow is simply the standard one.
        }
        return null;
    }

    // ------------------------------------------------------------------
    // The loop
    // ------------------------------------------------------------------

    @Override
    public void run() {
        try {
            step();
        } catch (Throwable t) {
            ShenanigansCore.getInstance().getLogger().warning("Fuga failed, cleaning up: " + t);
            clearWindupSlowness();
            Builder.cleanup(this);
            cancel();
        }
    }

    private void step() {
        ticksLived++;
        stateTicks++;

        // A caster who logs off, dies or changes world cancels the arrow before it flies.
        if ((state == State.WINDUP || state == State.FLIGHT)
                && (!caster.isOnline() || caster.isDead() || caster.getWorld() != startLocation.getWorld())) {
            clearWindupSlowness();
            Builder.cleanup(this);
            cancel();
            return;
        }

        switch (state) {
            case WINDUP -> {
                if (stateTicks == 1) beginWindup();
                double progress = Math.min(1.0, stateTicks / (double) windupTicks);
                direction = caster.getEyeLocation().getDirection().normalize();
                Builder.tickWindup(this, progress);
                if (stateTicks >= windupTicks) release();
            }
            case FLIGHT -> tickFlight();
            case IMPACT -> {
                if (stateTicks == 1) {
                    Builder.createExplosion(this);
                    applyDamage();
                }
                Builder.tickImpact(this, stateTicks);
                tickShake();
                if (stateTicks >= (domainBoost ? IMPACT_TICKS_DOMAIN : IMPACT_TICKS)) {
                    enter(State.DONE);
                }
            }
            case DONE -> {
                Builder.cleanup(this);
                cancel();
            }
        }

        // Ribbon, seal and flash-disc upkeep (shrink / fade / remove on schedule).
        if (state != State.DONE) Builder.tickAmbient(this);
    }

    private void enter(State next) {
        this.state = next;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------
    // WINDUP -> FLIGHT
    // ------------------------------------------------------------------

    private void beginWindup() {
        shrine = findShrine(caster);
        domainBoost = shrine != null;
        Builder.spawnArrow(this);

        // He plants his feet to draw.
        WindupSlownessCleanup.grant(caster, windupSlowness, windupTicks + 4, 3);
        caster.sendActionBar(MiniMessage.miniMessage().deserialize(
                domainBoost ? "<dark_red><bold>Fuga <red>— the shrine feeds the flame"
                        : "<dark_red>Fuga <gray>...drawing"));
    }

    /** Removes the windup root only if this technique granted it (never a stranger's). */
    private void clearWindupSlowness() {
        WindupSlownessCleanup.clear(caster, windupSlowness);
    }

    /** "Open": lock the aim, convert the bow into flight. */
    private void release() {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector aimDir = eye.getDirection().normalize();

        Location muzzle = rigTip();
        if (muzzle == null) muzzle = eye.clone().add(aimDir.clone().multiply(1.6));

        // Converge on the crosshair so the arrow hits what the caster looks at.
        RayTraceResult aim = world.rayTraceBlocks(eye, aimDir, 80.0, FluidCollisionMode.NEVER, true);
        Location aimPoint = aim != null
                ? aim.getHitPosition().toLocation(world)
                : eye.clone().add(aimDir.clone().multiply(80.0));
        Vector toAim = aimPoint.toVector().subtract(muzzle.toVector());
        if (toAim.lengthSquared() > 9.0) {
            direction = toAim.normalize();
        } else {
            direction = aimDir;
        }

        currentLocation = muzzle.clone();
        clearWindupSlowness();

        // Recoil, like a real draw-and-loose.
        Vector recoil = direction.clone().multiply(-0.30 * Math.min(power, 3.0));
        recoil.setY(0.08);
        caster.setVelocity(caster.getVelocity().add(recoil));

        caster.showTitle(Title.title(
                MiniMessage.miniMessage().deserialize(domainBoost ? "<dark_red><bold>開" : "<red><bold>開"),
                MiniMessage.miniMessage().deserialize("<dark_red>Fuga"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(450), Duration.ofMillis(250))));

        flightTicks = 0;
        traveled = 0.0;
        enter(State.FLIGHT);
        Builder.release(this);

        // Released inside a wall: it simply goes off on the spot.
        if (muzzle.getBlock().getType().isSolid()) {
            currentLocation = eye.clone();
            beginImpact();
        }
    }

    private Location rigTip() {
        return rig == null ? null : Builder.tipOf(rig);
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
                // Stand the blast just off the surface so it blooms in open air.
                at.add(hit.getHitBlockFace().getDirection().multiply(0.35));
            }
            currentLocation = at;
            Builder.tickFlight(this, prev, currentLocation);
            beginImpact();
            return;
        }

        currentLocation.add(direction.clone().multiply(speed));
        traveled += speed;
        Builder.tickFlight(this, prev, currentLocation);

        if (flightTicks > maxFlightTicks || traveled > maxRange
                || !world.isChunkLoaded(currentLocation.getBlockX() >> 4, currentLocation.getBlockZ() >> 4)) {
            beginImpact();
        }
    }

    // ------------------------------------------------------------------
    // IMPACT
    // ------------------------------------------------------------------

    private void beginImpact() {
        impactLocation = currentLocation.clone();
        shrine = findShrine(caster);
        domainBoost = shrine != null;
        impactRadius = (radiusBase + radiusPerPower * power) * (domainBoost ? domainRadiusMultiplier : 1.0);
        enter(State.IMPACT);
    }

    /** Falloff damage, fire and knockback. Blocked-by-wall victims take much less. */
    private void applyDamage() {
        World world = impactLocation.getWorld();
        if (world == null) return;
        double r = impactRadius;
        double mult = domainBoost ? domainDamageMultiplier : 1.0;
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (Entity raw : world.getNearbyEntities(impactLocation, r, r, r)) {
            if (!(raw instanceof LivingEntity victim)) continue;
            if (victim.equals(caster) || victim instanceof ArmorStand || !victim.isValid() || victim.isDead()) continue;
            if (victim instanceof Player pl
                    && (pl.getGameMode() == GameMode.CREATIVE || pl.getGameMode() == GameMode.SPECTATOR)) continue;

            Location chest = victim.getLocation().add(0, victim.getHeight() * 0.55, 0);
            double dist = chest.distance(impactLocation);
            if (dist > r) continue;

            double falloff = 1.0 - 0.65 * (dist / r);
            Vector away = chest.toVector().subtract(impactLocation.toVector());

            // Cover: a solid wall between the blast and the victim blunts it.
            if (dist > 1.0) {
                RayTraceResult cover = world.rayTraceBlocks(impactLocation, away.clone().normalize(),
                        dist, FluidCollisionMode.NEVER, true);
                if (cover != null) falloff *= 0.4;
            }

            double dmg = damagePerPower * power * falloff * mult;
            if (victim.equals(hitEntity)) dmg *= 1.25;
            dmg = MathUtil.hitDamage(dmg, damagePerPower, 1.0);

            // Sure-hit through invulnerability frames, like the shrine's cleave.
            victim.setNoDamageTicks(0);
            victim.damage(dmg, caster);
            victim.setFireTicks(Math.max(victim.getFireTicks(), (int) ((60 + power * 20) * (domainBoost ? 2 : 1))));
            Builder.burnAura(this, victim);

            Vector kb = away.lengthSquared() < 0.01 ? new Vector(0, 1, 0) : away.normalize();
            double force = (0.5 + 0.22 * power) * falloff * (domainBoost ? 1.4 : 1.0);
            kb.multiply(force);
            kb.setY(Math.max(kb.getY(), 0.0) + 0.35 * falloff + 0.1);
            if (kb.length() > 3.0) kb.normalize().multiply(3.0);
            victim.setVelocity(kb);

            if (victim instanceof Player pl) {
                pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<dark_red>Fuga <dark_gray>| <red>-" + (int) Math.ceil(dmg / 2.0) + "❤"
                                + (domainBoost ? " <gray>The shrine burns" : "")));
                registerShake(pl, 4.0 * falloff + 1.0);
            }
            if (rng.nextInt(3) == 0) {
                SoundUtils.play(chest, Sound.ENTITY_PLAYER_HURT_ON_FIRE, 0.8f, 0.8f + rng.nextFloat() * 0.4f);
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

        // A big, readable title for everyone caught in the shrine's eruption.
        if (domainBoost) {
            Title title = Title.title(
                    MiniMessage.miniMessage().deserialize("<dark_red><bold>爆"),
                    MiniMessage.miniMessage().deserialize("<red>The shrine ignites"),
                    Title.Times.times(Duration.ofMillis(60), Duration.ofMillis(900), Duration.ofMillis(300)));
            for (Player pl : world.getPlayers()) {
                if (pl.getLocation().distanceSquared(impactLocation) <= r * r * 2.2) pl.showTitle(title);
            }
        }
    }

    private void registerShake(Player p, double amplitude) {
        if (!cameraShake) return;
        Location l = p.getLocation();
        shakes.put(p.getUniqueId(), new float[]{l.getYaw(), l.getPitch(), (float) Math.min(amplitude, 6.0)});
    }

    private void tickShake() {
        if (!cameraShake || shakes.isEmpty() || stateTicks > SHAKE_TICKS) return;
        double decay = 1.0 - (stateTicks / (double) SHAKE_TICKS);
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

    /** The shooter. */
    public Player caster() {
        return caster;
    }

    /** The arrow's power (1.0–5.0). */
    public double power() {
        return power;
    }

    /** Where the cast began (the caster's eye). */
    public Location startLocation() {
        return startLocation;
    }

    /** Where the arrow tip is right now. */
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

    /** Length of the windup in ticks. */
    public int windupTicks() {
        return windupTicks;
    }

    /** Ticks the arrow has been flying. */
    public int flightTicks() {
        return flightTicks;
    }

    /** True when the caster's Malevolent Shrine is live (bigger, redder, deadlier Fuga). */
    public boolean domainBoost() {
        return domainBoost;
    }

    /** The caster's shrine session, or null. */
    public Session shrine() {
        return shrine;
    }

    /** Where the arrow detonated (valid from IMPACT on). */
    public Location impactLocation() {
        return impactLocation;
    }

    /** Blast radius in blocks (valid from IMPACT on). */
    public double impactRadius() {
        return impactRadius;
    }

    /** The living thing the arrow struck directly, or null. */
    public LivingEntity hitEntity() {
        return hitEntity;
    }

    /** Effect density multiplier (config quality, 0.4-2.0). */
    public double quality() {
        return quality;
    }

    /** How long the scorch mark lingers, in ticks. */
    public int scorchTicks() {
        return scorchTicks;
    }

    Builder.Rig rig() {
        return rig;
    }

    void setRig(Builder.Rig rig) {
        this.rig = rig;
    }
}