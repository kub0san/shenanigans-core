package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlowness;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.WindupSlownessCleanup;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Piercing Blood (穿血) — the shot's state machine.
 *
 * <p><b>From the wiki:</b> the user compresses and condenses their blood to the limit, claps their
 * hands together and fires the blood from their fingertips; reinforced with cursed energy, it
 * reaches the speed of sound. So the technique has exactly three beats, and so does this class:</p>
 *
 * <pre>
 *  COMPRESS  ~0.7–1.3s  blood is drawn out of the body and squeezed into a dense orb between the
 *                       hands. Power = compression: more power, longer draw, harder shot.
 *  CLAP      3 ticks    the palms slam together — the orb collapses to a point.
 *  BEAM                 the shot is resolved INSTANTLY (speed of sound ≈ 17 blocks a tick, so it is a
 *                       hit-scan): it pierces every living thing on its line (up to a cap), each
 *                       one taking a little less, and punches through thin blocks before it stops
 *                       on solid ground. The visuals then play out over ~2 seconds.
 *  DONE                 displays removed; wall decals fade out on their own.
 * </pre>
 *
 * <p>This class holds the <b>when and what</b> (timing, trace, damage); every particle, sound and
 * display lives in {@link PiercingBloodBuilder}.</p>
 *
 * <h3>Tuning</h3>
 * <p>Live keys: {@code abilities.techniques.piercing-blood.damage} (default 14.0),
 * {@code cooldown} and {@code armor-piercing}. All other values below are shipped constants.</p>
 * <ul>
 *   <li>{@code compress-base-ticks} (10), {@code compress-per-power} (3), {@code range} (100)</li>
 *   <li>{@code pierce-count} (4, plus 1 per 2 power),
 *       {@code pierce-falloff} (0.85 — each pierced target takes this fraction of the previous one)</li>
 *   <li>{@code pierce-hardness} (0.6 — blocks this soft or softer are punched through, e.g. glass,
 *       leaves, ice), {@code pierce-blocks} (2)</li>
 *   <li>{@code head-speed} (40 blocks per tick, visual only), {@code bleed-seconds} (2),
 *       {@code scar-seconds} (6), {@code quality} (1.0, range 0.4–2.0)</li>
 * </ul>
 */
public class PiercingBloodShot extends BukkitRunnable {
    /** Shipped per-power damage; live value comes from {@code abilities.techniques.piercing-blood.damage}. */
    public static final double DEFAULT_DAMAGE_PER_POWER = 14.0;

    /** Live per-power damage, swapped by {@code /bg reload}. */
    public static double configuredDamage() {
        return AbilityTuningConfig.current().damage("piercing-blood", DEFAULT_DAMAGE_PER_POWER);
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
        return AbilityTuningConfig.current().armorPiercing("piercing-blood", true);
    }


    /** The shot's phases, in order. */
    public enum State {
        /** Squeezing blood into an orb between the hands. */
        COMPRESS,
        /** The palms come together. */
        CLAP,
        /** The shot has been fired; its visuals are playing out. */
        BEAM,
        /** Finished. */
        DONE
    }

    /** A living thing the shot passed through. {@code distance} is measured along the beam. */
    public record EntityHit(LivingEntity entity, double distance, Location point) {}

    /** A soft block the shot punched through. */
    public record Punch(Location point, BlockFace face, BlockData data) {}

    /** Where the shot finally stopped on a solid block. */
    public record Impact(Location point, BlockFace face, BlockData data) {}

    /** The resolved path of the shot: always a straight line from {@code start} along {@code dir}. */
    public static final class Trace {
        final Location start;
        final Vector dir;
        final double length;
        final List<EntityHit> hits;
        final List<Punch> punches;
        final Impact impact;

        Trace(Location start, Vector dir, double length, List<EntityHit> hits,
              List<Punch> punches, Impact impact) {
            this.start = start;
            this.dir = dir;
            this.length = length;
            this.hits = hits;
            this.punches = punches;
            this.impact = impact;
        }
    }
    /** Ticks the palms take to meet. */
    static final int CLAP_TICKS = 3;
    /** Ticks the beam phase lasts (the visuals need ~2 seconds). */
    static final int BEAM_TICKS = 44;

    // ── Tunables (read once per cast) ──
    private final int compressTicks;
    private final double range;
    private final double damagePerPower;
    private final int pierceCount;
    private final double pierceFalloff;
    private final double pierceHardness;
    private final int pierceBlocks;
    private final boolean armorPiercing;
    private final double headSpeed;
    private final int bleedTicks;
    private final int scarTicks;
    private final double quality;

    // ── Cast state ──
    private final Player caster;
    private final double power;
    private Vector direction;
    private State state;

    /** Tracks the windup root so release/cancel removes only what we granted. */
    private final WindupSlowness windupSlowness = new WindupSlowness();
    private int ticksLived;
    private int stateTicks;

    private Trace trace;
    private PiercingBloodBuilder.Rig rig;

    /**
     * @param caster the user
     * @param power  compression (1.0–5.0) — scales draw time, damage, thickness and penetration
     */
    public PiercingBloodShot(Player caster, double power) {
        this.caster = caster;
        this.power = power;
        this.direction = caster.getLocation().getDirection().normalize();
        this.state = State.COMPRESS;

        int base = clampInt(10, 4, 60);
        int per = clampInt(3, 0, 12);
        this.compressTicks = clampInt(base + (int) Math.round(per * power), 4, 120);
        this.range = MathUtil.clamp(100.0, 10.0, 300.0);
        this.damagePerPower = effectiveDamage(configuredDamage());
        this.pierceCount = clampInt(4, 1, 20) + (int) (power / 2.0);
        this.pierceFalloff = MathUtil.clamp(0.85, 0.1, 1.0);
        this.pierceHardness = MathUtil.clamp(0.6, 0.0, 5.0);
        this.pierceBlocks = clampInt(2, 0, 8);
        this.armorPiercing = armorPiercingEnabled();
        this.headSpeed = MathUtil.clamp(40.0, 10.0, 120.0);
        this.bleedTicks = clampInt((int) Math.round(2.0 * 20.0), 0, 400);
        this.scarTicks = clampInt((int) Math.round(6.0 * 20.0), 20, 1200);
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
            ShenanigansCore.getInstance().getLogger().warning("Piercing Blood failed, cleaning up: " + t);
            clearWindupSlowness();
            PiercingBloodBuilder.cleanup(this);
            cancel();
        }
    }

    private void step() {
        ticksLived++;
        stateTicks++;

        // A caster who logs off or dies cancels the draw before it fires.
        if ((state == State.COMPRESS || state == State.CLAP)
                && (!caster.isOnline() || caster.isDead())) {
            clearWindupSlowness();
            PiercingBloodBuilder.cleanup(this);
            cancel();
            return;
        }

        switch (state) {
            case COMPRESS -> {
                if (stateTicks == 1) beginCompress();
                double progress = Math.min(1.0, stateTicks / (double) compressTicks);
                PiercingBloodBuilder.tickCompress(this, progress);
                if (stateTicks >= compressTicks) enter(State.CLAP);
            }
            case CLAP -> {
                PiercingBloodBuilder.tickClap(this, stateTicks);
                if (stateTicks >= CLAP_TICKS) fire();
            }
            case BEAM -> {
                PiercingBloodBuilder.tickBeam(this, stateTicks);
                if (stateTicks >= BEAM_TICKS) enter(State.DONE);
            }
            case DONE -> {
                PiercingBloodBuilder.cleanup(this);
                cancel();
            }
        }

        if (state != State.DONE) PiercingBloodBuilder.tickAmbient(this);
    }

    private void enter(State next) {
        this.state = next;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------
    // COMPRESS -> CLAP -> BEAM
    // ------------------------------------------------------------------

    private void beginCompress() {
        PiercingBloodBuilder.spawnCompress(this);
        // He plants his feet while the blood is squeezed.
        WindupSlownessCleanup.grant(caster, windupSlowness, compressTicks + CLAP_TICKS + 2, 3);
    }

    /** Removes the windup root only if this technique granted it (never a stranger's). */
    private void clearWindupSlowness() {
        WindupSlownessCleanup.clear(caster, windupSlowness);
    }

    private boolean canHit(Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isValid() || le.isDead()) return false;
        if (e.equals(caster) || e instanceof ArmorStand) return false;
        if (e instanceof Player p) {
            GameMode mode = p.getGameMode();
            return mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
        }
        return true;
    }

    /** The shot: resolved instantly, then handed to the builder to play out. */
    private void fire() {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector aimDir = eye.getDirection().normalize();

        Location muzzle = rig == null ? null : PiercingBloodBuilder.muzzleOf(rig);
        if (muzzle == null) muzzle = eye.clone().add(aimDir.clone().multiply(1.9));

        // Converge on the crosshair so the shot lands where the caster looks.
        RayTraceResult aim = world.rayTrace(eye, aimDir, 60.0, FluidCollisionMode.NEVER, true, 0.3, this::canHit);
        Location aimPoint = aim != null
                ? aim.getHitPosition().toLocation(world)
                : eye.clone().add(aimDir.clone().multiply(60.0));
        direction = PiercingBloodAim.direction(aimDir, muzzle.toVector(), aimPoint.toVector());

        clearWindupSlowness();

        trace = trace(world, muzzle, direction);
        applyDamage();

        // Recoil: firing at the speed of sound kicks back.
        Vector recoil = direction.clone().multiply(-0.28 * Math.min(power, 3.0));
        recoil.setY(0.06);
        caster.setVelocity(caster.getVelocity().add(recoil));

        enter(State.BEAM);
        PiercingBloodBuilder.fire(this);
    }

    // ------------------------------------------------------------------
    // The trace (hit-scan with pierce)
    // ------------------------------------------------------------------

    /**
     * Walks the beam: collects every living thing on the line, punches through soft blocks, and
     * stops on the first solid block (or at max range). The beam never bends, so the result is
     * just a length plus the things it touched.
     */
    private Trace trace(World world, Location start, Vector dir) {
        List<EntityHit> hits = new ArrayList<>();
        List<Punch> punches = new ArrayList<>();
        Impact impact = null;
        Set<UUID> seen = new HashSet<>();

        Location pos = start.clone();
        double traveled = 0.0;
        int blocksPierced = 0;

        for (int iter = 0; iter < 8 && traveled < range; iter++) {
            double remaining = range - traveled;
            RayTraceResult blockHit = world.rayTraceBlocks(pos, dir, remaining, FluidCollisionMode.NEVER, true);
            double segLen = blockHit == null ? remaining : pos.toVector().distance(blockHit.getHitPosition());

            collectEntities(world, pos, dir, segLen, traveled, hits, seen);

            if (blockHit == null) {
                traveled = range;
                break;
            }

            traveled += segLen;
            Block block = blockHit.getHitBlock();
            BlockFace face = blockHit.getHitBlockFace() == null ? BlockFace.UP : blockHit.getHitBlockFace();
            Location hitPoint = blockHit.getHitPosition().toLocation(world);

            if (block != null && blocksPierced < pierceBlocks) {
                float hardness = block.getType().getHardness();
                if (hardness >= 0f && hardness <= pierceHardness) {
                    punches.add(new Punch(hitPoint, face, block.getBlockData()));
                    blocksPierced++;
                    pos = hitPoint.clone().add(dir.clone().multiply(1.05));
                    traveled += 1.05;
                    continue;
                }
            }
            if (block != null) impact = new Impact(hitPoint, face, block.getBlockData());
            break;
        }

        hits.sort(Comparator.comparingDouble(EntityHit::distance));
        while (hits.size() > pierceCount) hits.remove(hits.size() - 1);

        double length = Math.max(0.5, Math.min(range, traveled));
        return new Trace(start.clone(), dir.clone(), length, hits, punches, impact);
    }

    private void collectEntities(World world, Location pos, Vector dir, double segLen, double traveledBefore,
                                 List<EntityHit> out, Set<UUID> seen) {
        Vector a = pos.toVector();
        Vector b = a.clone().add(dir.clone().multiply(segLen));
        BoundingBox box = BoundingBox.of(a, b).expand(1.5);

        for (Entity e : world.getNearbyEntities(box)) {
            if (!canHit(e) || seen.contains(e.getUniqueId())) continue;
            RayTraceResult r = e.getBoundingBox().expand(0.2).rayTrace(a, dir, segLen);
            if (r == null) continue;
            seen.add(e.getUniqueId());
            double along = traveledBefore + a.distance(r.getHitPosition());
            out.add(new EntityHit((LivingEntity) e, along, r.getHitPosition().toLocation(world)));
        }
    }

    // ------------------------------------------------------------------
    // Damage
    // ------------------------------------------------------------------

    /** Every pierced target takes damage; each successive one takes a bit less. */
    private void applyDamage() {
        List<EntityHit> hits = trace.hits;
        for (int i = 0; i < hits.size(); i++) {
            LivingEntity victim = hits.get(i).entity();
            if (!victim.isValid() || victim.isDead()) continue;

            double dmg = MathUtil.hitDamage(
                    damagePerPower * power * Math.pow(pierceFalloff, i), damagePerPower, 1.0);

            // Sure-hit through invulnerability frames.
            victim.setNoDamageTicks(0);
            dealDamage(victim, dmg);

            // A short shove along the beam.
            Vector kb = direction.clone().multiply(0.45 + 0.1 * power);
            kb.setY(0.1);
            victim.setVelocity(victim.getVelocity().add(kb));

            if (victim instanceof Player pl) {
                pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                        "<dark_red>Piercing Blood <dark_gray>| <red>-" + (int) Math.ceil(dmg / 2.0) + "❤"));
            }
        }
    }

    /** Armor-piercing damage (the blood is reinforced with cursed energy), with a safe fallback. */
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
    // Accessors for PiercingBloodBuilder (same package)
    // ------------------------------------------------------------------

    /** The user. */
    public Player caster() {
        return caster;
    }

    /** Compression (1.0–5.0). */
    public double power() {
        return power;
    }

    /** The aim direction (follows the caster until the shot is fired). */
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

    /** Length of the compress phase in ticks. */
    public int compressTicks() {
        return compressTicks;
    }

    /** The resolved shot (valid from the BEAM phase on). */
    public Trace trace() {
        return trace;
    }

    /** How fast the beam's visual head travels, in blocks per tick. */
    public double headSpeed() {
        return headSpeed;
    }

    /** How long pierced targets visibly bleed, in ticks. */
    public int bleedTicks() {
        return bleedTicks;
    }

    /** How long wall decals linger, in ticks. */
    public int scarTicks() {
        return scarTicks;
    }

    /** Effect density multiplier (config quality, 0.4-2.0). */
    public double quality() {
        return quality;
    }

    PiercingBloodBuilder.Rig rig() {
        return rig;
    }

    void setRig(PiercingBloodBuilder.Rig rig) {
        this.rig = rig;
    }
}
