package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.unlimitedvoid;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.Barrier;
import com.hizeric.shenaniganscore.core.display.BarrierShatterAnimator;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TitleAnimator;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.session.DomainExitController;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.session.SessionEndReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Owns the whole Unlimited Void sequence, its state and its cleanup.
 *
 * <pre>
 *  SIGN       1.6s  the typed title; the blindfold comes off; crossed fingers flash at Gojo's hand.
 *                   Quietly, the black hole, haze, stars and streaks are built as invisible seeds.
 *  EXPANSION  3.2s  THE CUT. A white-hot wavefront races out of Gojo; each viewer's screen whites out as
 *                   it passes, splits into colored horizontal bands, slides, splatters, goes black.
 *                   Behind that cover the floor and the black dome are built.
 *  IGNITION   2.4s  the reveal: the black hole's ring draws itself on and its disk spins up, stars
 *                   fade in, haze bands and faint streaks drift in. No shockwave: cold and quiet.
 *  DOMAIN     live  staged sure-hit; victims drown in glyphs; Gojo wears an infinity aura
 *  COLLAPSE   2.4s  captives released; the black hole folds into its core; the dome comes down
 * </pre>
 *
 * <p>The shell is the shared visual-only {@link Barrier} (membrane curtain while forming, then a
 * persistent display-entity dome), the ground is the real
 * {@link Floor} platform. Public fields and methods other classes rely on
 * ({@code victimStages}, {@code exposureTicks}, {@link #isVoidOpen()}, {@link #isLockedIn},
 * {@link #end}) are unchanged.</p>
 *
 * Config ({@code jujutsukaisen.unlimitedvoid.*}): {@code domain-seconds}, {@code caster-buffs},
 * {@code onset-seconds}, {@code quality} (0.4-2.0, default 1.0).
 */
public class Session extends DomainSession {

    /** Sure-hit refresh interval in ticks. */
    public static final int OVERLOAD_STEP_TICKS = 5;

    private static final double SIGN_SECONDS = 1.6;
    private static final double EXPANSION_SECONDS = 3.2;
    private static final double IGNITION_SECONDS = 2.4;
    private static final double DEFAULT_DOMAIN_SECONDS = 20.0;
    private static final double COLLAPSE_SECONDS = 2.4;

    /** Share of the expansion over which the dome and floor are built (hidden by the Cut). */
    private static final double BUILD_SHARE = 0.72;

    private static final double SHELL_COLLAPSE_SHARE = 1.0 - Barrier.FLOOR_LEAD_IN;
    private static final double DEFAULT_ONSET_SECONDS = 0.5;

    private static final String DOMAIN_TITLE = "領域展開";
    private static final String VOID_TITLE = "無量空処";

    private final Player caster;
    private final long startTime;

    private final double signSeconds;
    private final double expansionSeconds;
    private final double ignitionSeconds;
    private final double domainSeconds;
    private final double collapseSeconds;
    private final double totalSeconds;
    private final int onsetTicks;

    /** Density multiplier (config {@code quality}). */
    public final double quality;

    // ---- visual state ----

    public final List<Entity> spawnedDisplays = new ArrayList<>();

    /** The void's ground: a real, flat, plain-black platform. */
    public final Floor floor = new Floor(this);

    /** Where the black hole hangs: behind and above Gojo, as victims see him. */
    public final Location eyeCenter;
    public final Vector gaze;

    /** Visual-only shell: a membrane curtain while forming, then a persistent display-entity dome. */
    public final Barrier shell = createShell();

    private BlackHole blackHole;
    private Haze haze;
    private Starfield starfield;
    private Streaks streaks;
    private Cut cut;
    private final List<BlockDisplay> signProps = new ArrayList<>();

    // ---- sure-hit state ----

    public final Map<UUID, Integer> exposureTicks = new HashMap<>();
    /** Current stage per victim (0 onset, 1 locked, 2 deep). Rebuilt every refresh. */
    public final Map<UUID, Integer> victimStages = new HashMap<>();
    public final Set<UUID> immobilizedPlayers = ConcurrentHashMap.newKeySet();
    public final List<LivingEntity> aiLockedMobs = new ArrayList<>();
    public final Map<UUID, List<TextDisplay>> halos = new HashMap<>();

    private final Map<UUID, PlayerTimeState> savedPlayerTimes = new HashMap<>();
    private final BukkitRunnable task;
    /** Owns the shatter presentation while the caster is dead; null otherwise. */
    private BarrierShatterAnimator shatterAnimator;
    private final boolean casterBuffsEnabled;

    private boolean signShown;
    private boolean fingersShown;
    private boolean expansionStarted;
    private boolean ignited;
    private boolean domainStarted;
    private boolean collapseStarted;
    private int built;

    private boolean grantedResistance;
    private boolean grantedSpeed;

    private int lastOverloadStep = -1;
    private int lastHudStep = -1;
    private int lastAmbientStep = -1;
    private int lastTwinkleStep = -1;
    private int lastTitleStep = -1;

    public Session(Player caster, double radius) {
        super(caster, caster.getLocation(), radius);
        this.caster = caster;
        this.startTime = System.currentTimeMillis();

        this.casterBuffsEnabled = true;

        double q = 1.0;
        if (Double.isNaN(q) || q <= 0.0) q = 1.0;
        this.quality = Math.max(0.4, Math.min(2.0, q));

        this.signSeconds = SIGN_SECONDS;
        this.expansionSeconds = EXPANSION_SECONDS;
        this.ignitionSeconds = IGNITION_SECONDS;
        double configured = DEFAULT_DOMAIN_SECONDS;
        this.domainSeconds = configured < 1.0 || configured > 120.0
                ? DEFAULT_DOMAIN_SECONDS : configured;
        this.collapseSeconds = COLLAPSE_SECONDS;

        double onset = DEFAULT_ONSET_SECONDS;
        if (onset < 0.0 || onset > 10.0) onset = DEFAULT_ONSET_SECONDS;
        this.onsetTicks = (int) Math.round(onset * 20.0);

        this.totalSeconds = signSeconds + expansionSeconds + ignitionSeconds
                + domainSeconds + collapseSeconds;

        Vector look = caster.getLocation().getDirection().setY(0);
        this.gaze = look.lengthSquared() < 1.0e-4 ? new Vector(0, 0, 1) : look.normalize();
        // Behind Gojo from the victims' side, and up: he stands framed against the ring.
        this.eyeCenter = getCenter().clone()
                .add(gaze.clone().multiply(-0.62 * radius))
                .add(0, 0.34 * radius, 0);

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                    if (elapsed >= totalSeconds) {
                        cancel();
                        close();
                        return;
                    }

                    double expansionEnd = signSeconds + expansionSeconds;
                    double ignitionEnd = expansionEnd + ignitionSeconds;
                    double domainEnd = ignitionEnd + domainSeconds;

                    if (elapsed < signSeconds) {
                        tickSign(elapsed);
                    } else if (elapsed < expansionEnd) {
                        tickExpansion(elapsed - signSeconds);
                    } else if (elapsed < ignitionEnd) {
                        tickIgnition(elapsed - expansionEnd, elapsed);
                    } else if (elapsed < domainEnd) {
                        tickDomain(elapsed - ignitionEnd, elapsed);
                    } else {
                        tickCollapse(elapsed - domainEnd, elapsed);
                    }

                    // The platform spends its tick budget every tick, so it is never left half-built.
                    floor.platform().tick();
                    shell.tick();
                    tickVerticalHandling();
                    if (blackHole != null) blackHole.tick();
                    if (haze != null) haze.tick();
                    if (streaks != null) streaks.tick();
                } catch (Throwable t) {
                    ShenanigansCore.getInstance().getLogger()
                            .warning("Unlimited Void tick failed, closing domain: " + t);
                    cancel();
                    close();
                }
            }
        };
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** The shell is anchored on the platform: the wall grows out of the floor. */
    private Barrier createShell() {
        Barrier barrier = new Barrier(
                getCenter(), getRadius(), false,
                Material.BLACK_CONCRETE, Material.BLACK_CONCRETE,
                Material.WHITE_CONCRETE);
        barrier.setShellFloor(floor.surfaceY());
        return barrier;
    }
    public void start() {
        if (floor != null && floor.platform() != null) {
            floor.platform().setEntityRestoreManaged(true);
        }
        task.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    public Player casterPlayer() {
        return caster;
    }

    /** True while the sure-hit is live (from opening until the collapse begins). */
    public boolean isVoidOpen() {
        return domainStarted && !collapseStarted;
    }

    public int getOnsetTicks() {
        return onsetTicks;
    }

    /** True once this victim has been exposed long enough to be hard-locked. */
    public boolean isLockedIn(UUID id) {
        int exposure = exposureTicks.getOrDefault(id, 0);
        return exposure > 0 && exposure >= onsetTicks;
    }

    /** The Unlimited Void sure-hit hard-lock, exposed through the core session seam. */
    @Override
    public boolean isMovementLocked(Player player) {
        if (player == null || !isActive() || !isVoidOpen()) return false;
        if (player.getUniqueId().equals(getCaster().getUniqueId())) return false;
        Location center = getCenter();
        if (center == null || player.getWorld() != center.getWorld()) return false;
        double radius = getRadius();
        return player.getLocation().distanceSquared(center) <= radius * radius
                && isLockedIn(player.getUniqueId());
    }

    public void registerVisual(Entity entity) {
        if (entity != null) spawnedDisplays.add(entity);
    }

    /** How many points on the black hole's disk victims' information streams can flow from. */
    public int sourceCount() {
        return blackHole == null ? 0 : blackHole.sourceCount();
    }

    /** A live point on the black hole's disk (replaces the old constellation network). */
    public Location sourcePoint(int index, double elapsed) {
        return blackHole == null ? eyeCenter.clone() : blackHole.sourcePoint(index);
    }

    // ------------------------------------------------------------------
    // Phases
    // ------------------------------------------------------------------

    private void tickSign(double elapsed) {
        World world = getCenter().getWorld();
        if (!signShown) {
            signShown = true;
            shell.prepare(); // start snapshotting chunks now so the dome is ready behind the Cut
            floor.prepare(); // measure the terrain so the platform can be planned
            if (world != null) {
                world.playSound(getCenter(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.2f, 0.55f);
                world.playSound(getCenter(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.1f, 0.5f);
                world.playSound(getCenter(), Sound.BLOCK_BELL_RESONATE, 0.8f, 0.6f);
            }
        }

        // Build the heavy visuals as invisible seeds, a few ticks apart so no single tick spikes.
        buildStage(elapsed);

        // The domain-expansion title types itself in, glyph by glyph.
        int titleStep = (int) (elapsed / 0.15);
        if (titleStep > lastTitleStep) {
            lastTitleStep = titleStep;
            boolean last = elapsed >= signSeconds - 0.25;
            Title title = TitleAnimator.build(DOMAIN_TITLE, VOID_TITLE, "Unlimited Void", elapsed, last);
            showDomainTitle(world, title);
        }

        // The crossed fingers flash in the last stretch of the sign.
        if (!fingersShown && elapsed >= signSeconds - 0.45) {
            fingersShown = true;
            showCrossedFingers(world);
        }
    }

    /** Builds one piece per call, spread across the sign phase. */
    private void buildStage(double elapsed) {
        Location bhAt = eyeCenter;
        double r = getRadius();
        if (built == 0 && elapsed >= 0.30) {
            built = 1;
            blackHole = new BlackHole(bhAt, gaze, r * 0.1, quality, spawnedDisplays);
        } else if (built == 1 && elapsed >= 0.50) {
            built = 2;
            haze = new Haze(getCenter(), r, quality, spawnedDisplays);
        } else if (built == 2 && elapsed >= 0.70) {
            built = 3;
            starfield = new Starfield(getCenter(), r, quality, spawnedDisplays);
        } else if (built == 3 && elapsed >= 0.90) {
            built = 4;
            streaks = new Streaks(getCenter().clone().add(0, 1.0, 0), r, quality);
        }
    }

    /** Two thin white bars crossing in front of Gojo's hand, the sign that precedes the Cut. */
    private void showCrossedFingers(World world) {
        if (world == null || !caster.isOnline()) return;
        Location eye = caster.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location at = eye.clone().add(dir.multiply(0.75)).add(right.multiply(0.28)).add(0, -0.3, 0);
        float yaw = caster.getLocation().getYaw() - 90f;

        for (float lean : new float[]{35f, -35f}) {
            BlockDisplay bar = DisplayUtils.spawnBlock(at, Material.WHITE_CONCRETE, 3.0f, 15);
            bar.setGlowing(true);
            bar.setGlowColorOverride(Color.fromRGB(150, 220, 255));
            TransformUtils.applyTransform(bar, 0.01f, 0.01f, 0.01f, yaw, lean, 0f, 0);
            registerVisual(bar);
            signProps.add(bar);
            ShenanigansCore.runTaskLater( () -> {
                if (bar.isValid()) TransformUtils.applyTransform(bar, 0.03f, 0.03f, 0.6f, yaw, lean, 0f, 4);
            }, 1L);
        }
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.8f);
        world.spawnParticle(Particle.END_ROD, at, 10, 0.15, 0.15, 0.15, 0.02);
    }

    private void clearSignProps() {
        for (BlockDisplay bar : signProps) {
            if (bar.isValid()) bar.remove();
        }
        signProps.clear();
    }

    /**
     * Shows the animated domain-expansion title to the caster and to everyone standing inside the
     * void's radius.
     */
    private void showDomainTitle(World world, Title title) {
        if (world == null) return;
        double r2 = getRadius() * getRadius();
        for (Player p : world.getPlayers()) {
            if (p.equals(caster) || p.getLocation().distanceSquared(getCenter()) <= r2) {
                p.showTitle(title);
            }
        }
    }

    /** THE CUT. The wavefront and every viewer's screen are driven by {@link Cut}; this builds behind it. */
    private void tickExpansion(double local) {
        World world = getCenter().getWorld();

        if (!expansionStarted) {
            expansionStarted = true;
            clearSignProps();
            if (world != null) {
                world.playSound(getCenter(), Sound.ENTITY_WARDEN_EMERGE, 1.6f, 0.45f);
                world.playSound(getCenter(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.3f, 0.55f);
            }
            // Safety net in case the sign phase was cut short.
            while (built < 4) {
                built = 4;
                if (blackHole == null) blackHole = new BlackHole(eyeCenter, gaze, getRadius() * 0.1, quality, spawnedDisplays);
                if (haze == null) haze = new Haze(getCenter(), getRadius(), quality, spawnedDisplays);
                if (starfield == null) starfield = new Starfield(getCenter(), getRadius(), quality, spawnedDisplays);
                if (streaks == null) streaks = new Streaks(getCenter().clone().add(0, 1.0, 0), getRadius(), quality);
            }

            cut = new Cut(getCenter().clone().add(0, 1.0, 0), getRadius(), quality, viewers(), streaks);
            cut.start();
        }

        // Hidden behind the white: the barrier owns the order (floor lead-in, then the shell).
        double progress = Math.min(1.0, local / Math.max(0.05, expansionSeconds * BUILD_SHARE));
        shell.setFormationProgress(progress);
        floor.expand(shell.floorProgress());
        shell.setFloorFrontRadius(floor.builtRadius());
    }

    /** Everyone who should see the Cut: the caster and every player inside the radius. */
    private List<Player> viewers() {
        List<Player> out = new ArrayList<>();
        World world = getCenter().getWorld();
        if (world == null) return out;
        double r2 = getRadius() * getRadius();
        for (Player p : world.getPlayers()) {
            if (p.equals(caster) || p.getLocation().distanceSquared(getCenter()) <= r2) out.add(p);
        }
        return out;
    }

    /** The reveal: the black hole ignites, quietly. */
    private void tickIgnition(double local, double total) {
        if (!ignited) {
            ignited = true;
            World world = getCenter().getWorld();
            if (world != null) {
                world.playSound(eyeCenter, Sound.BLOCK_BEACON_ACTIVATE, 1.1f, 0.5f);
                world.playSound(eyeCenter, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.9f, 0.9f);
            }

            // Safety net: the floor lead-in may have been cut short by a lag spike.
            floor.finish();
            shell.setFormationProgress(1.0);
            applyVoidSky();

            if (blackHole != null) blackHole.ignite((int) Math.round(ignitionSeconds * 20.0 * 0.9));
            if (haze != null) haze.show(59);
            if (starfield != null) starfield.ignite();

            if (caster.isOnline()) {
                caster.showTitle(Title.title(
                        Component.empty(),
                        MiniMessage.miniMessage().deserialize("<aqua><italic>Infinity opens..."),
                        Title.Times.times(Duration.ofMillis(80),
                                Duration.ofMillis(1200), Duration.ofMillis(250))));
            }
        }
    }

    private void tickDomain(double elapsed, double total) {
        World world = getCenter().getWorld();

        if (!domainStarted) {
            domainStarted = true;
            shell.setFormationProgress(1.0);
            if (world != null) {
                world.playSound(eyeCenter, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 0.55f);
                world.playSound(eyeCenter, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 0.8f, 1.2f);
            }
            if (casterBuffsEnabled) applyCasterBuffs();
        }

        // Sure-hit + information flood + Gojo's aura, four times a second.
        int overloadStep = (int) (elapsed / (OVERLOAD_STEP_TICKS / 20.0));
        if (overloadStep > lastOverloadStep) {
            lastOverloadStep = overloadStep;
            applyVoidSky();
            Overload.apply(this, caster);
            Limitless.tick(this, total);
            Limitless.tickAura(this, total);
        }

        // A few stars flare up every ~0.7s.
        int twinkleStep = (int) (elapsed / 0.7);
        if (twinkleStep > lastTwinkleStep) {
            lastTwinkleStep = twinkleStep;
            if (starfield != null) starfield.twinkle();
        }

        int hudStep = (int) (elapsed / 0.35);
        if (hudStep > lastHudStep) {
            lastHudStep = hudStep;
            sendVoidHud(elapsed);
        }

        int ambientStep = (int) (elapsed / 2.0);
        if (ambientStep > lastAmbientStep) {
            lastAmbientStep = ambientStep;
            if (world != null) {
                world.playSound(eyeCenter, Sound.BLOCK_CONDUIT_AMBIENT, 0.35f, 1.45f);
            }
        }
    }

    private void tickCollapse(double elapsed, double total) {
        World world = getCenter().getWorld();
        int ticks = (int) Math.round(collapseSeconds * 20.0);

        if (!collapseStarted) {
            collapseStarted = true;
            cleanupVerticalHandling();
            releaseCaptives();
            DomainExitController.returnCaptives(this);
            if (blackHole != null) blackHole.collapse(Math.min(59, ticks));
            if (haze != null) haze.collapse(Math.min(59, ticks));
            if (starfield != null) starfield.collapse(Math.min(59, ticks));
            if (streaks != null) streaks.collapse(Math.min(59, ticks), eyeCenter);

            if (world != null) {
                world.playSound(eyeCenter, Sound.BLOCK_BEACON_DEACTIVATE, 1.6f, 0.55f);
                world.playSound(eyeCenter, Sound.BLOCK_PORTAL_TRIGGER, 1.1f, 0.8f);
            }
            if (caster.isOnline()) {
                caster.showTitle(Title.title(
                        Component.empty(),
                        MiniMessage.miniMessage().deserialize("<gray><italic>無量空処"),
                        Title.Times.times(Duration.ofMillis(80),
                                Duration.ofMillis(900), Duration.ofMillis(250))));
            }
        }

        double progress = MathUtil.clamp(elapsed / Math.max(0.05, collapseSeconds), 0.0, 1.0);
        double eased = MathUtil.smoothstep(progress);

        // The reverse of the formation: the crown goes first and the floor pole last.
        shell.setFormationProgress(1.0 - eased);

        // The floor is the last thing to leave: it only un-spreads once the walls are gone.
        double floorPhase = MathUtil.clamp(
                (eased - SHELL_COLLAPSE_SHARE) / (1.0 - SHELL_COLLAPSE_SHARE), 0.0, 1.0);
        shell.setFloorFrontRadius(Math.max(0.0, floorRadius() * (1.0 - floorPhase)));
        floor.collapse(floorPhase);
    }

    /** The platform's radius, in blocks. */
    private double floorRadius() {
        return Math.min(floor.builtRadius(), getRadius());
    }

    // ------------------------------------------------------------------
    // Caster, HUD, sky
    // ------------------------------------------------------------------

    private void applyCasterBuffs() {
        if (!caster.isOnline()) return;
        int durationTicks = (int) Math.round((domainSeconds + collapseSeconds) * 20.0);

        PotionEffect resistance = caster.getPotionEffect(PotionEffectType.RESISTANCE);
        if (resistance == null || resistance.getAmplifier() < 1
                || resistance.getDuration() < durationTicks) {
            caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                    durationTicks, 1, false, false, true));
            grantedResistance = true;
        }

        PotionEffect speed = caster.getPotionEffect(PotionEffectType.SPEED);
        if (speed == null || speed.getAmplifier() < 1
                || speed.getDuration() < durationTicks) {
            caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    durationTicks, 1, false, false, true));
            grantedSpeed = true;
        }
    }

    private void sendVoidHud(double elapsed) {
        if (!caster.isOnline()) return;
        double remaining = Math.max(0.0, domainSeconds - elapsed);
        int bars = 24;
        int filled = Math.min(bars, Math.max(0,
                (int) Math.round((elapsed / domainSeconds) * bars)));
        String bar = "<dark_gray>" + "▰".repeat(filled) + "<gray>" + "▱".repeat(bars - filled);
        caster.sendActionBar(MiniMessage.miniMessage().deserialize(
                bar + " <dark_aqua>無量空処 <white>" + (int) Math.ceil(remaining) + "s"
                        + " <gray>· <aqua>" + victimStages.size() + " <gray>caught"));
    }

    private void applyVoidSky() {
        World world = getCenter().getWorld();
        if (world == null) return;
        double radiusSquared = getRadius() * getRadius();

        for (Player player : world.getPlayers()) {
            boolean inside = player.getLocation().distanceSquared(getCenter()) <= radiusSquared;
            UUID uuid = player.getUniqueId();

            if (inside) {
                savedPlayerTimes.computeIfAbsent(uuid, id -> new PlayerTimeState(
                        player.getPlayerTime(), player.isPlayerTimeRelative()));
                Starfield.applyVoidSky(player);
                continue;
            }

            PlayerTimeState state = savedPlayerTimes.remove(uuid);
            if (state != null && player.isOnline()) {
                player.setPlayerTime(state.time(), state.relative());
            }
        }

        var iterator = savedPlayerTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PlayerTimeState> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            if (player.getWorld() == world) continue;
            PlayerTimeState state = entry.getValue();
            player.setPlayerTime(state.time(), state.relative());
            iterator.remove();
        }
    }

    /** Frees everyone the void was holding. Safe to call more than once. */
    private void releaseCaptives() {
        for (LivingEntity mob : aiLockedMobs) {
            if (mob.isValid()) mob.setAI(true);
        }
        aiLockedMobs.clear();
        immobilizedPlayers.clear();
        exposureTicks.clear();
        victimStages.clear();
        Limitless.clearAll(this);
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /**
     * Ends the domain with the outro that matches the reason. A caster that dies inside the void
     * shatters the shell in fracture fronts; every other ending keeps the reverse collapse.
     */
    @Override
    public void end(SessionEndReason reason) {
        if (!isActive()) return;
        if (reason == SessionEndReason.CASTER_DEATH) {
            if (task != null && !task.isCancelled()) task.cancel();
            collapseStarted = true;
            cleanupVerticalHandling();
            releaseCaptives();
            DomainExitController.returnCaptives(this);

            shatterAnimator = new BarrierShatterAnimator(
                    getCenter(), getRadius(),
                    Material.BLACK_CONCRETE.createBlockData(),
                    floor.surfaceY());
            if (!shatterAnimator.play(
                    shell::shatter,
                    () -> floor.platform() != null && floor.platform().shatter(),
                    this::finishShatter)) {
                shatterAnimator = null;
                close();
                return;
            }
            // Keep session active for presentation callbacks; visuals owned by animator.
            return;
        }
        close();
    }

    /** Called by the shatter presentation once its visuals are done. */
    private void finishShatter() {
        shatterAnimator = null;
        if (shell != null) shell.close();
        if (floor != null) floor.close();
        // Complete remaining cleanup if the session is still active.
        if (isActive()) {
            finalizeCloseState();
        }
    }

    @Override
    public void close() {
        if (!isActive()) return;
        if (task != null && !task.isCancelled()) task.cancel();

        // If no shatter presentation is active, ensure captives are returned/cleaned up.
        if (shatterAnimator == null) {
            cleanupVerticalHandling();
            releaseCaptives();
            DomainExitController.returnCaptives(this);
        }

        finalizeCloseState();
    }

    @Override
    public void closeForShutdown() {
        // disable() cancels the scheduler immediately after stopAll(), so the
        // shatter's finish callback would never run. Stop the presentation now and
        // clear the reference so finalizeCloseState() closes the real floor and
        // shell synchronously.
        if (shatterAnimator != null) {
            shatterAnimator.close();
            shatterAnimator = null;
        }
        close();
    }

    private void finalizeCloseState() {
        if (!isActive()) return;

        if (caster.isOnline()) {
            if (grantedResistance) {
                PotionEffect effect = caster.getPotionEffect(PotionEffectType.RESISTANCE);
                if (effect != null && effect.getAmplifier() == 1) {
                    caster.removePotionEffect(PotionEffectType.RESISTANCE);
                }
            }
            if (grantedSpeed) {
                PotionEffect effect = caster.getPotionEffect(PotionEffectType.SPEED);
                if (effect != null && effect.getAmplifier() == 1) {
                    caster.removePotionEffect(PotionEffectType.SPEED);
                }
            }
        }

        if (cut != null) cut.close();
        if (blackHole != null) blackHole.close();
        if (haze != null) haze.close();
        if (starfield != null) starfield.close();
        if (streaks != null) streaks.close();
        clearSignProps();
        cleanupVerticalHandling();
        releaseCaptives();

        for (Map.Entry<UUID, PlayerTimeState> entry : savedPlayerTimes.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) continue;
            PlayerTimeState state = entry.getValue();
            player.setPlayerTime(state.time(), state.relative());
        }
        savedPlayerTimes.clear();

        // During a shatter presentation the shell and floor belong to the animator until it finishes.
        if (shatterAnimator == null) {
            if (shell != null) shell.close();
            if (floor != null) floor.close();
        }

        for (Entity entity : spawnedDisplays) {
            if (entity != null && entity.isValid()) entity.remove();
        }
        spawnedDisplays.clear();

        deactivate();
        ShenanigansCore.getInstance().getSessionManager().removeSession(this);
    }

    private record PlayerTimeState(long time, boolean relative) {}
}
