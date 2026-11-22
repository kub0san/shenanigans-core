package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine.Session;
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
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * World Cutting Slash — the state machine.
 *
 * <p><b>Design (from the lore):</b> the slash does not cut the target; it cuts the <i>space the target is in</i>,
 * which is why it ignores Infinity, Simple Domain and every other defense. It is not unavoidable though: it is
 * nearly invisible but still has to be sensed, and it costs a Binding Vow — the chant and hand sign — which is
 * a visible warning. So the technique is built around a <b>telegraph and a dodge window</b>:</p>
 *
 * <pre>
 *  CHANT   16 ticks  the hand sign is formed; the sky tints crimson; kanji orbit and are drawn into the hands.
 *  CUT      ~8+      the cut planes are locked and a hairline seam unzips along each one, leaving a thin slit
 *                    in the terrain. Nothing is hurt yet.
 *  WAIT    12 ticks  the dodge window. The seam hangs in the air; the space has been cut but not yet severed.
 *  SEVER   14 ticks  the seam opens into a black gap. Anything still touching a plane at this moment takes
 *                    the cut: a percentage of max health that ignores armor, effects and totems.
 *  CLOSE   12 ticks  the gap closes. The slit in the terrain returns a few seconds later.
 * </pre>
 *
 * <p><b>Modes:</b> a single plane (vertical, or horizontal if the caster sneaks), or a lattice of vertical planes
 * ("grid") with safe pockets between them. If the caster owns a live Malevolent Shrine the planes are far larger:
 * the cut covers the whole domain.</p>
 *
 * <p>The only live key is {@code abilities.techniques.world-slash.armor-piercing} (swapped by
 * {@code /bg reload}); world-slashes always deal a fixed fraction of max health. All other tuning
 * values are constants below. All visuals live in {@link WorldSlashBuilder}.
 * Nothing real in the world is ever changed.</p>
 */
public class WorldSlashCast extends BukkitRunnable {
    /** Live armor-piercing toggle, swapped by {@code /bg reload}. */
    public static boolean armorPiercingEnabled() {
        return AbilityTuningConfig.current().armorPiercing("world-slash", true);
    }


    /** The cast's phases, in order. */
    public enum State {
        /** Hand sign and chant. */
        CHANT,
        /** Seams unzip along the planes. */
        CUT,
        /** The dodge window. */
        WAIT,
        /** The cut is severed: damage is applied. */
        SEVER,
        /** The gap closes. */
        CLOSE,
        /** Finished. */
        DONE
    }

    // ── Timing (ticks) ──
    static final int CHANT_TICKS = 16;
    static final int UNZIP_TICKS = 6;
    static final int STAGGER_TICKS = 2;
    static final int WAIT_TICKS = 12;
    static final int SEVER_TICKS = 14;
    static final int CLOSE_TICKS = 12;

    // ── Geometry (blocks) ──
    static final double SINGLE_LENGTH = 40.0;
    static final double SINGLE_HALF = 10.0;
    static final double GRID_SPACING = 3.0;
    static final double GRID_EXTENT = 7.5;
    static final double GRID_HALF = 8.0;
    static final double DOMAIN_LENGTH = 70.0;
    static final double DOMAIN_HALF = 20.0;
    static final double DOMAIN_GRID_SPACING = 6.0;
    static final double DOMAIN_GRID_EXTENT = 20.0;
    static final double DOMAIN_GRID_HALF = 14.0;
    /** Half the thickness of a plane for hit tests (an entity within this distance of the plane is cut). */
    static final double THICKNESS_HALF = 0.45;

    // ── Damage ──
    static final double PERCENT_SINGLE = 0.40;
    static final double PERCENT_GRID = 0.35;
    static final double DOMAIN_MULT = 1.5;
    static final double FLAT_DAMAGE = 4.0;

    // ── Visual limits ──
    static final int MAX_SLIT_BLOCKS = 2500;
    static final int SLIT_RESTORE_TICKS = 100;
    private static final int SHAKE_TICKS = 8;

    /**
     * One flat sheet of cut space. Points on the sheet are {@code origin + a*ta + b*tb} with ta in [a0, a1]
     * and tb in [-bHalf, +bHalf]; {@code n} is the normal (the sheet's thin direction).
     */
    public static final class CutPlane {
        final Location origin;
        final Vector a;
        final Vector b;
        final Vector n;
        final double a0;
        final double a1;
        final double bHalf;

        CutPlane(Location origin, Vector a, Vector b, Vector n, double a0, double a1, double bHalf) {
            this.origin = origin;
            this.a = a.clone().normalize();
            this.b = b.clone().normalize();
            this.n = n.clone().normalize();
            this.a0 = a0;
            this.a1 = a1;
            this.bHalf = bHalf;
        }

        /** The point on the sheet at (ta, tb), as a vector. */
        Vector at(double ta, double tb) {
            return origin.toVector()
                    .add(a.clone().multiply(ta))
                    .add(b.clone().multiply(tb));
        }

        Location locAt(double ta, double tb) {
            Vector v = at(ta, tb);
            return new Location(origin.getWorld(), v.getX(), v.getY(), v.getZ());
        }

        /** True when p lies on the sheet (within its thickness plus {@code tol}). */
        boolean touches(Vector p, double tol) {
            Vector d = p.clone().subtract(origin.toVector());
            double ta = d.dot(a);
            double tb = d.dot(b);
            double tn = d.dot(n);
            return ta >= a0 - tol && ta <= a1 + tol
                    && Math.abs(tb) <= bHalf + tol
                    && Math.abs(tn) <= THICKNESS_HALF + tol;
        }

        /** Which side of the sheet p is on: +1 or -1. */
        double side(Vector p) {
            double tn = p.clone().subtract(origin.toVector()).dot(n);
            return tn >= 0.0 ? 1.0 : -1.0;
        }
    }

    /** A living thing the cut severed, with the plane it touched and which side of it it was on. */
    public record Hit(LivingEntity entity, int plane, double side) {}

    // ── Cast state ──
    private final Player caster;
    private final boolean grid;
    private final boolean horizontal;
    private final boolean armorPiercing;
    private State state;
    private int ticksLived;
    private int stateTicks;

    private boolean enhanced;
    private Session shrine;
    private final List<CutPlane> planes = new ArrayList<>();
    private Vector flat = new Vector(0, 0, 1);
    private Vector right = new Vector(-1, 0, 0);
    private Location centre;
    private int cutTicks;
    private final Set<UUID> hit = new HashSet<>();
    private final Map<UUID, float[]> shakes = new HashMap<>();
    private int shakeLeft;

    private WorldSlashBuilder.Rig rig;

    /**
     * @param caster the user
     * @param grid   true for a lattice of planes instead of a single cut
     */
    public WorldSlashCast(Player caster, boolean grid) {
        this.caster = caster;
        this.grid = grid;
        this.horizontal = !grid && caster.isSneaking();
        this.state = State.CHANT;
        this.centre = caster.getEyeLocation();
        this.armorPiercing = armorPiercingEnabled();
    }

    /** Starts the state machine on the server tick loop. */
    public void start() {
        this.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    /** The caster's own live Malevolent Shrine, or null. */
    static Session findShrine(Player caster) {
        try {
            for (DomainSession s : ShenanigansCore.getInstance().getSessionManager().getActiveSessions().values()) {
                if (s instanceof Session sh && sh.isActive()
                        && sh.casterPlayer().getUniqueId().equals(caster.getUniqueId())
                        && sh.getDomainElapsed() > 0.0) {
                    return sh;
                }
            }
        } catch (Throwable ignored) {
            // No shrine info is never fatal: the cut is simply the standard one.
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
            ShenanigansCore.getInstance().getLogger().warning("World Cutting Slash failed, cleaning up: " + t);
            WorldSlashBuilder.cleanup(this);
            cancel();
        }
    }

    private void step() {
        ticksLived++;
        stateTicks++;

        // A caster who logs off or dies during the chant cancels the cut.
        if (state == State.CHANT && (!caster.isOnline() || caster.isDead())) {
            WorldSlashBuilder.cleanup(this);
            cancel();
            return;
        }

        switch (state) {
            case CHANT -> {
                if (stateTicks == 1) beginChant();
                double progress = Math.min(1.0, stateTicks / (double) CHANT_TICKS);
                WorldSlashBuilder.tickChant(this, progress);
                if (stateTicks >= CHANT_TICKS) {
                    lockPlanes();
                    enter(State.CUT);
                }
            }
            case CUT -> {
                if (stateTicks == 1) WorldSlashBuilder.beginCut(this);
                WorldSlashBuilder.tickCut(this, stateTicks);
                if (stateTicks >= cutTicks) enter(State.WAIT);
            }
            case WAIT -> {
                WorldSlashBuilder.tickWait(this, stateTicks);
                if (stateTicks >= WAIT_TICKS) enter(State.SEVER);
            }
            case SEVER -> {
                if (stateTicks == 1) {
                    List<Hit> hits = applySever();
                    WorldSlashBuilder.beginSever(this, hits);
                }
                WorldSlashBuilder.tickSever(this, stateTicks);
                tickShake();
                if (stateTicks >= SEVER_TICKS) enter(State.CLOSE);
            }
            case CLOSE -> {
                WorldSlashBuilder.tickClose(this, stateTicks);
                if (stateTicks >= CLOSE_TICKS) enter(State.DONE);
            }
            case DONE -> {
                WorldSlashBuilder.cleanup(this);
                cancel();
            }
        }

        if (state != State.DONE) WorldSlashBuilder.tickAmbient(this);
    }

    private void enter(State next) {
        this.state = next;
        this.stateTicks = 0;
    }

    // ------------------------------------------------------------------
    // CHANT
    // ------------------------------------------------------------------

    private void beginChant() {
        shrine = findShrine(caster);
        enhanced = shrine != null;
        WorldSlashBuilder.spawnChant(this);
        // He stands still while the Binding Vow is spoken.
        caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                CHANT_TICKS + 4, 4, false, false, true));
    }

    // ------------------------------------------------------------------
    // LOCK: define the cut planes
    // ------------------------------------------------------------------

    private void lockPlanes() {
        World world = caster.getWorld();
        Location eye = caster.getEyeLocation();
        Vector look = eye.getDirection();

        flat = new Vector(look.getX(), 0, look.getZ());
        if (flat.lengthSquared() < 1.0e-4) flat = new Vector(0, 0, 1);
        flat.normalize();
        Vector up = new Vector(0, 1, 0);
        right = flat.clone().crossProduct(up).normalize();

        shrine = findShrine(caster);
        enhanced = shrine != null;

        planes.clear();
        if (!grid) {
            double length = enhanced ? DOMAIN_LENGTH : SINGLE_LENGTH;
            double half = enhanced ? DOMAIN_HALF : SINGLE_HALF;
            if (horizontal) {
                Location origin = new Location(world, eye.getX(), caster.getLocation().getY() + 1.0, eye.getZ());
                planes.add(new CutPlane(origin, flat, right, up, 1.5, length, half));
            } else {
                planes.add(new CutPlane(eye.clone(), flat, up, right, 1.5, length, half));
            }
            centre = planes.get(0).locAt((1.5 + length) / 2.0, 0);
        } else {
            double spacing = enhanced ? DOMAIN_GRID_SPACING : GRID_SPACING;
            double extent = enhanced ? DOMAIN_GRID_EXTENT : GRID_EXTENT;
            double half = enhanced ? DOMAIN_GRID_HALF : GRID_HALF;

            Location g;
            if (enhanced && shrine != null) {
                g = shrine.getCenter().clone();
            } else {
                RayTraceResult aim = world.rayTraceBlocks(eye, look.clone().normalize(), 30.0, FluidCollisionMode.NEVER, true);
                double dist = aim != null ? Math.max(10.0, eye.toVector().distance(aim.getHitPosition()) - 1.0) : 20.0;
                g = eye.clone().add(look.clone().normalize().multiply(dist));
            }
            centre = g.clone();

            // Plane offsets from the centre, half a cell off-centre so the middle of the lattice is a safe pocket
            // (spacing 3 gives -4.5, -1.5, +1.5, +4.5).
            List<Double> offsets = new ArrayList<>();
            for (double s = -extent + spacing / 2.0; s <= extent - spacing / 2.0 + 1.0e-6; s += spacing) offsets.add(s);

            // First the planes running along the caster's facing, left to right…
            for (double s : offsets) {
                Location o = g.clone().add(right.clone().multiply(s)).subtract(flat.clone().multiply(extent));
                planes.add(new CutPlane(o, flat, up, right, 0.0, extent * 2.0, half));
            }
            // …then the planes crossing them, near to far.
            for (double s : offsets) {
                Location o = g.clone().add(flat.clone().multiply(s)).subtract(right.clone().multiply(extent));
                planes.add(new CutPlane(o, right, up, flat, 0.0, extent * 2.0, half));
            }
        }

        cutTicks = 2 + (planes.size() - 1) * STAGGER_TICKS + UNZIP_TICKS;
    }

    // ------------------------------------------------------------------
    // SEVER: damage
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

    /**
     * Everything still touching a plane is cut. This is checked NOW, not when the seam was drawn, so anyone who
     * stepped out of the plane during the dodge window is safe.
     */
    private List<Hit> applySever() {
        List<Hit> hits = new ArrayList<>();
        World world = caster.getWorld();
        if (planes.isEmpty()) return hits;

        // Everything inside the bounding box of all the planes is a candidate.
        BoundingBox box = null;
        for (CutPlane p : planes) {
            for (double ta : new double[]{p.a0, p.a1}) {
                for (double tb : new double[]{-p.bHalf, p.bHalf}) {
                    Vector v = p.at(ta, tb);
                    if (box == null) box = BoundingBox.of(v, v);
                    else box.union(v);
                }
            }
        }
        if (box == null) return hits;
        box.expand(2.0);

        for (Entity e : world.getNearbyEntities(box)) {
            if (!canHit(e) || hit.contains(e.getUniqueId())) continue;
            LivingEntity victim = (LivingEntity) e;
            double tol = victim.getWidth() / 2.0;
            Location base = victim.getLocation();
            Vector[] samples = {
                    base.clone().add(0, 0.1, 0).toVector(),
                    base.clone().add(0, victim.getHeight() * 0.5, 0).toVector(),
                    base.clone().add(0, Math.max(0.1, victim.getHeight() - 0.1), 0).toVector()
            };

            int touched = -1;
            double side = 1.0;
            for (int i = 0; i < planes.size() && touched < 0; i++) {
                for (Vector s : samples) {
                    if (planes.get(i).touches(s, tol)) {
                        touched = i;
                        side = planes.get(i).side(samples[1]);
                        break;
                    }
                }
            }
            if (touched < 0) continue;

            hit.add(victim.getUniqueId());
            sever(victim, planes.get(touched), side);
            hits.add(new Hit(victim, touched, side));
        }
        return hits;
    }

    /** The cut itself: a percentage of max health, ignoring armor, effects and totems; then a shove along the normal. */
    private void sever(LivingEntity victim, CutPlane plane, double side) {
        double percent = grid ? PERCENT_GRID : PERCENT_SINGLE;
        double amount = victim.getMaxHealth() * percent * (enhanced ? DOMAIN_MULT : 1.0) + FLAT_DAMAGE;

        victim.setNoDamageTicks(0);
        dealDamage(victim, amount);

        // The two halves of the cut space move apart: shove along the normal, toward the side it was on.
        Vector kb = plane.n.clone().multiply(side * 0.7);
        kb.setY(kb.getY() + 0.12);
        if (!victim.isDead()) victim.setVelocity(kb);

        if (victim instanceof Player pl) {
            pl.sendActionBar(MiniMessage.miniMessage().deserialize(
                    "<dark_red>World Cutting Slash <dark_gray>| <red>-" + (int) Math.ceil(amount / 2.0) + "❤"));
            registerShake(pl, 5.0);
        }
    }

    /** Damage that bypasses armor, effects, absorption-style defenses and totems, with safe fallbacks. */
    private void dealDamage(LivingEntity victim, double amount) {
        if (!armorPiercing) {
            victim.damage(amount, caster);
            return;
        }
        try {
            DamageSource source = DamageSource.builder(DamageType.GENERIC_KILL)
                    .withCausingEntity(caster)
                    .withDirectEntity(caster)
                    .build();
            victim.damage(amount, source);
            return;
        } catch (Throwable ignored) {
            // Fall through to the milder sources.
        }
        try {
            DamageSource source = DamageSource.builder(DamageType.MAGIC)
                    .withCausingEntity(caster)
                    .withDirectEntity(caster)
                    .build();
            victim.damage(amount, source);
            return;
        } catch (Throwable ignored) {
            // Fall through to plain damage.
        }
        victim.damage(amount, caster);
    }

    // ------------------------------------------------------------------
    // Camera shake
    // ------------------------------------------------------------------

    private void registerShake(Player p, double amplitude) {
        Location l = p.getLocation();
        shakes.put(p.getUniqueId(), new float[]{l.getYaw(), l.getPitch(), (float) Math.min(amplitude, 6.0)});
        shakeLeft = SHAKE_TICKS;
    }

    private void tickShake() {
        if (shakeLeft <= 0 || shakes.isEmpty()) return;
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
    // Accessors for WorldSlashBuilder (same package)
    // ------------------------------------------------------------------

    /** The user. */
    public Player caster() {
        return caster;
    }

    /** True for grid mode. */
    public boolean isGrid() {
        return grid;
    }

    /** True for a horizontal single cut (the caster sneaked). */
    public boolean isHorizontal() {
        return horizontal;
    }

    /** True when the caster's Malevolent Shrine is live (the cut covers the whole domain). */
    public boolean isEnhanced() {
        return enhanced;
    }

    /** The caster's shrine session, or null. */
    public Session shrine() {
        return shrine;
    }

    /** The locked cut planes (valid from CUT on). */
    public List<CutPlane> planes() {
        return planes;
    }

    /** The middle of the cut (valid from CUT on). */
    public Location centre() {
        return centre;
    }

    /** Duration of the CUT phase in ticks (valid from CUT on). */
    public int cutTicks() {
        return cutTicks;
    }

    /** The caster's horizontal facing at the moment the planes were locked. */
    public Vector flat() {
        return flat;
    }

    /** The caster's horizontal right-hand direction at the moment the planes were locked. */
    public Vector right() {
        return right;
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

    WorldSlashBuilder.Rig rig() {
        return rig;
    }

    void setRig(WorldSlashBuilder.Rig rig) {
        this.rig = rig;
    }
}
