package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.Barrier;
import com.hizeric.shenaniganscore.core.display.BarrierShatterAnimator;
import com.hizeric.shenaniganscore.core.display.TitleAnimator;
import com.hizeric.shenaniganscore.core.particle.ParticleUtils;
import com.hizeric.shenaniganscore.core.session.DomainExitController;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.session.SessionEndReason;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Time Cell Moon Palace timeline.
 *
 * <pre>
 *  SIGN       1.0s  the title types itself in, glitching; the projector spools up; film spirals
 *  EXPANSION  6.0s  the terrain blackens outward (the floor lead-in), then the shell rises
 *                   continuously from the studio floor to the crown; the sky drains to midnight;
 *                   film ribbons unroll from above; the flesh path unfurls toward the eye;
 *                   the lid cracks into a slit of light and parts
 *  DOMAIN    16.0s  CUT TO BLACK. A scan line sweeps from the eye across the arena and injects
 *                   film into every neck as it passes. The projector beam flickers down the
 *                   path, the flesh beats, ribbons run, the 24 FPS rule is enforced
 *  COLLAPSE  10.0s  everyone thaws; the lids close and the last light goes out; the path
 *                   winds back front-to-eye; ribbons snap; the shell breaks down from the crown
 *                   down to the floor pole and only then does the blackout lift; the world returns
 * </pre>
 *
 * <p><b>Animation language:</b> organic things move smoothly, film things move in whole
 * frames of a 24 FPS clock.</p>
 *
 * <p>Layout: {@link Barrier} (void + shell), {@link Stage} (path, eye, lobes),
 * {@link Ribbons} (film curtains), {@link Projector} (light), {@link Rule24} (sure-hit),
 * {@link Enforcer} (listener), {@link Film} (visuals), {@link Sky}. All per-domain state
 * lives here or in those objects; no globals.</p>
 */
public class Session extends DomainSession {
    private final Player caster;
    private final long startTime;
    private final float casterYaw;
    private final Location origin;

    final Settings cfg;
    final Barrier blackout;
    final Floor floor;
    final Stage stage;
    final Ribbons ribbons;
    final Rule24 rule;
    private final Enforcer enforcer;

    /** Seconds of the expansion spent on the floor lead-in (blackout + path). */
    private final double floorSeconds;
    /** Seconds of the expansion spent on the shell rising from the bottom pole. */
    private final double shellSeconds;

    /** Every display this domain spawns, for guaranteed cleanup. */
    public final List<Entity> spawnedDisplays = new ArrayList<>();

    private final Set<UUID> skyPlayers = new HashSet<>();
    /** Players the cutToBlack flash blinded; cleared back to them on close. */
    private final BlindnessGrants blindness = new BlindnessGrants();
    private long skyStartTime = 0L;
    private boolean speedGranted = false;
    private Location casterLast;

    private final double totalSeconds;
    private final BukkitRunnable task;
    /** Owns the shatter presentation while the caster is dead; null otherwise. */
    private BarrierShatterAnimator shatterAnimator;

    private int phase = -1;
    private boolean signShown, expandInit, domainInit, collapseStarted, failedOnce, scanDone, endFlash;
    private final int[] lastStep = new int[16];

    private static final double SCAN_SECONDS = 0.9;
    private static final String MAIN_TITLE = "領域展開";
    private static final String SUB_TITLE = "時胞月宮殿";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    public Session(Player caster, double radius) {
        super(caster, caster.getLocation(), radius);
        this.caster = caster;
        this.startTime = System.currentTimeMillis();
        this.casterYaw = caster.getLocation().getYaw();
        this.origin = caster.getLocation().clone();
        this.casterLast = caster.getLocation();
        Arrays.fill(lastStep, -1);

        this.cfg = Settings.load();
        this.totalSeconds = cfg.signSeconds + cfg.expandSeconds + cfg.domainSeconds + cfg.collapseSeconds;

        // The barrier splits the expansion in two: the floor lead-in (the real
        // studio floor and the flesh path) and the shell that rises behind it.
        this.floorSeconds = cfg.expandSeconds * Barrier.FLOOR_LEAD_IN;
        this.shellSeconds = cfg.expandSeconds * (1.0 - Barrier.FLOOR_LEAD_IN);

        this.floor = new Floor(this);
        // The interior blackout is gone: the real platform hides the terrain
        // far cheaper, and the shell only has to close the walls.
        this.blackout = new Barrier(origin, radius, false,
                Material.BLACK_CONCRETE, Material.BLACK_CONCRETE,
                Material.WHITE_CONCRETE);
        this.blackout.setShellFloor(floor.surfaceY());
        this.stage = new Stage(this);
        this.ribbons = new Ribbons(this);
        this.rule = new Rule24(this);
        this.enforcer = new Enforcer(this);

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
                if (elapsed >= totalSeconds) {
                    this.cancel();
                    close();
                    return;
                }
                try {
                    double t = elapsed;
                    if (t <= cfg.signSeconds) {
                        enterPhase(0);
                        tickSign(t);
                    } else if (t <= cfg.signSeconds + cfg.expandSeconds) {
                        enterPhase(1);
                        tickExpand(t - cfg.signSeconds);
                    } else if (t <= cfg.signSeconds + cfg.expandSeconds + cfg.domainSeconds) {
                        enterPhase(2);
                        tickDomain(t - cfg.signSeconds - cfg.expandSeconds);
                    } else {
                        enterPhase(3);
                        tickCollapse(t - cfg.signSeconds - cfg.expandSeconds - cfg.domainSeconds);
                    }
                    // The platform spends its tick budget every tick, so it is
                    // never left half-built.
                    floor.platform().tick();
                    blackout.tick();
                    tickVerticalHandling();
                } catch (Throwable ex) {
                    // One bad tick must not wedge the domain: log once, keep the timeline (and cleanup) alive
                    if (!failedOnce) {
                        failedOnce = true;
                        ShenanigansCore.getInstance().getLogger().warning("[TimeCellMoonPalace] tick error: " + ex);
                    }
                }
            }
        };
    }
    public void start() {
        if (floor != null && floor.platform() != null) {
            floor.platform().setEntityRestoreManaged(true);
        }
        Bukkit.getPluginManager().registerEvents(enforcer, ShenanigansCore.getInstance());
        this.task.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Accessors / geometry helpers (shared with Stage, Ribbons, Barrier, Rule24, Enforcer)
    // ─────────────────────────────────────────────────────────────────────────

    public Player casterPlayer() { return caster; }
    public float getCasterYaw() { return casterYaw; }
    Location origin() { return origin; }
    World world() { return origin.getWorld(); }

    /** Film frame index at 24 FPS, independent of server TPS. */
    long frame() {
        return (long) ((System.currentTimeMillis() - startTime) * Settings.FPS / 1000.0);
    }

    /** Local frame (+z = where the caster faced, x = across, y = relative to the caster's feet) to world. */
    Location local(double x, double y, double z) {
        double rad = Math.toRadians(casterYaw);
        return new Location(origin.getWorld(),
                origin.getX() + (x * Math.cos(rad)) - (z * Math.sin(rad)),
                origin.getY() + y,
                origin.getZ() + (x * Math.sin(rad)) + (z * Math.cos(rad)));
    }

    /** World to local {x, z}. */
    double[] toLocal(Location l) {
        double dx = l.getX() - origin.getX();
        double dz = l.getZ() - origin.getZ();
        double rad = Math.toRadians(casterYaw);
        return new double[]{dx * Math.cos(rad) + dz * Math.sin(rad), -dx * Math.sin(rad) + dz * Math.cos(rad)};
    }

    /**
     * The standing height of the ground.
     *
     * <p>Inside the domain this is the studio floor — one flat plane for the
     * whole arena, so the flesh path and the projector beam always sit on
     * the same level.</p>
     */
    double groundY(double wx, double wz) {
        if (floor != null && floor.platform() != null) {
            return floor.surfaceY();
        }
        World w = world();
        int bx = (int) Math.floor(wx);
        int bz = (int) Math.floor(wz);
        if (w == null || !w.isChunkLoaded(bx >> 4, bz >> 4)) return origin.getY();
        double y = w.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
        return Math.abs(y - origin.getY()) > 6.0 ? origin.getY() : y;
    }

    /** Inside the sealed sphere (with a small margin so nobody stands in the shell). */
    boolean inside(Location l) {
        if (l.getWorld() == null || !l.getWorld().equals(origin.getWorld())) return false;
        double r = Math.max(1.0, getRadius() - 0.8);
        return l.distanceSquared(origin) <= r * r;
    }

    boolean barrierApplies(Player p) {
        GameMode gm = p.getGameMode();
        return gm != GameMode.CREATIVE && gm != GameMode.SPECTATOR;
    }

    private void enterPhase(int p) {
        if (phase == p) return;
        phase = p;
        Arrays.fill(lastStep, -1);
    }

    /** True once per {@code interval} seconds of {@code t}. Jitter-proof (step counter); use a fixed interval per slot. */
    private boolean due(int slot, double t, double interval) {
        if (t < 0) return false;
        int step = (int) (t / interval);
        if (step > lastStep[slot]) {
            lastStep[slot] = step;
            return true;
        }
        return false;
    }

    private void refreshSkyPlayers() {
        World world = world();
        if (world == null) return;
        double r2 = getRadius() * getRadius();
        for (Player p : world.getPlayers()) {
            boolean in = p.getLocation().distanceSquared(origin) <= r2;
            if (in) {
                if (skyPlayers.add(p.getUniqueId()) && phase >= 2) Sky.apply(p);
            } else if (skyPlayers.remove(p.getUniqueId())) {
                Sky.reset(p);
            }
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SIGN: the title types itself in, the projector spools up
    // ═════════════════════════════════════════════════════════════════════════

    private void tickSign(double t) {
        World world = world();
        if (world == null) return;

        if (!signShown) {
            signShown = true;
            world.playSound(origin, Sound.BLOCK_CHAIN_PLACE, 2.0f, 0.6f);
            world.playSound(origin, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
            world.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, 1.6f, 0.5f);
            // The heavy terrain capture is spread over the next ticks, hidden behind the title
            blackout.prepare();
            // The studio floor is measured in the same window.
            floor.prepare();
        }

        if (due(2, t, 0.15)) sendSignTitle(t);

        // Projector spooling up: clicks that rise in pitch
        if (due(0, t, 0.1)) {
            world.playSound(origin, Sound.BLOCK_COMPARATOR_CLICK, 1.0f, (float) (0.6 + t * 0.7));
        }

        // Film ribbon spiralling around the caster: white and black frames alternating
        Location c = caster.getLocation();
        for (int k = 0; k < 4; k++) {
            double ang = t * 11.0 + k * (Math.PI / 2);
            double y = (t * 2.4 + k * 0.5) % 2.2;
            Particle.DustOptions dust = (k & 1) == 0 ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST;
            world.spawnParticle(Particle.DUST, c.getX() + Math.cos(ang) * 0.95, c.getY() + y, c.getZ() + Math.sin(ang) * 0.95,
                    1, 0, 0, 0, 0, dust);
        }
        if (due(1, t, 0.15)) {
            world.spawnParticle(Particle.WHITE_ASH, c.clone().add(0, 1.2, 0), 8, 1.2, 0.8, 1.2, 0);
        }
    }

    /**
     * The domain name types itself in one glyph at a time (full-width spaces hold the layout),
     * the subtitle follows, then a random glyph glitches. Shown to everyone inside the sphere.
     */
    private void sendSignTitle(double t) {
        World world = world();
        if (world == null) return;

        boolean last = t >= cfg.signSeconds - 0.25;
        Title title = TitleAnimator.build(MAIN_TITLE, SUB_TITLE, "Time Cell Moon Palace", t, last);

        double r2 = getRadius() * getRadius();
        for (Player p : world.getPlayers()) {
            if (p.equals(caster) || p.getLocation().distanceSquared(origin) <= r2) p.showTitle(title);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // EXPANSION: void, shell, ribbons, path, eye
    // ═════════════════════════════════════════════════════════════════════════

    private void tickExpand(double t) {
        World world = world();
        if (world == null) return;

        if (!expandInit) {
            expandInit = true;
            skyStartTime = world.getTime();
            refreshSkyPlayers();
            stage.build(floorSeconds, shellSeconds);
            ribbons.build(floorSeconds, shellSeconds);
            world.playSound(origin, Sound.ENTITY_WARDEN_EMERGE, 3.0f, 0.4f);
            world.playSound(origin, Sound.BLOCK_END_PORTAL_SPAWN, 1.0f, 0.5f);
            Projector.flash(origin.clone().add(0, 1.0, 0));
        }

        // The film runs at a fixed rate; the barrier front itself advances continuously.
        double reel = MathUtil.clamp(t / Math.max(0.05, cfg.expandSeconds), 0.0, 1.0);

        // The world drains to midnight
        if (due(2, t, 0.5)) refreshSkyPlayers();
        for (UUID id : skyPlayers) {
            Player pl = Bukkit.getPlayer(id);
            // Domain sky must not follow a player into another world (F-45).
            if (pl != null && pl.isOnline() && pl.getWorld() == world()) Sky.fadeIn(pl, skyStartTime, MathUtil.smoothstep(reel));
        }

        // The void: the studio floor forms during the lead-in, then the shell
        // rises from the floor's edge and closes at the crown.
        blackout.setFormationProgress(FormationProgress.continuous(reel));
        floor.expand(blackout.floorProgress());
        blackout.setFloorFrontRadius(floor.builtRadius());
        spawnClosingRim(world, blackout.shellProgress());
        shockwave(t, 0.15);
        shockwave(t, 1.10);

        stage.tickExpand(t, floorSeconds, shellSeconds);
        ribbons.tickExpand(t);

        if (due(3, t, 0.5)) world.playSound(origin, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
        if (due(4, t, 0.25)) world.playSound(origin, Sound.BLOCK_COMPARATOR_CLICK, 0.8f, 0.8f);
    }

    /** A floor ring that advances in whole frames (12 steps a second). */
    private void shockwave(double t, double t0) {
        double age = t - t0;
        if (age < 0) return;
        double radius = Math.floor(age * 12.0) / 12.0 * (getRadius() * 0.8);
        if (radius < 1.0 || radius > getRadius() - 2.0) return;
        Projector.shockRing(this, radius, frame());
    }

    /**
     * A ring of pale and black dust riding the latitude where the shell is forming.
     *
     * <p>The barrier owns that latitude — it reads it straight from the shell,
     * so the rim always sits exactly on the forming front, whether the wall
     * grows from a floor or from the bottom pole.</p>
     */
    private void spawnClosingRim(World world, double shellProgress) {
        double shellP = MathUtil.clamp(shellProgress, 0.0, 1.0);
        if (shellP <= 0.0 || shellP >= 1.0) return;
        double R = getRadius();
        double dy = blackout.frontOffset();
        double ringR = Math.sqrt(Math.max(0.0, R * R - dy * dy)) - 1.0;
        if (ringR < 1.0) return;
        int pts = (int) Math.min(72, Math.max(20, ringR * 2.5));
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2 * i) / pts;
            world.spawnParticle(Particle.DUST, origin.getX() + Math.cos(a) * ringR, origin.getY() + dy,
                    origin.getZ() + Math.sin(a) * ringR, 1, 0, 0, 0, 0, (i & 1) == 0 ? ParticleUtils.WHITE_DUST : ParticleUtils.BLACK_DUST);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DOMAIN
    // ═════════════════════════════════════════════════════════════════════════

    private void tickDomain(double t) {
        World world = world();
        if (world == null) return;

        if (!domainInit) {
            domainInit = true;
            blackout.setFormationProgress(1.0);
            floor.finish();
            stage.finishExpand();
            ribbons.finishExpand();
            for (UUID id : skyPlayers) {
                Player pl = Bukkit.getPlayer(id);
                if (pl != null && pl.isOnline() && pl.getWorld() == world()) Sky.apply(pl);
            }
            applyCasterBuffs();
            cutToBlack();
            rule.begin();
            world.playSound(origin, Sound.ENTITY_GENERIC_EXPLODE, 1.6f, 0.5f);
            world.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.4f);
            Projector.flash(stage.pupilLocation());
        }

        // ── The scan line: from the eye across the arena. Film goes in as it passes ──
        if (!scanDone) tickScan(t);

        // ── Sure-hit ──
        rule.tick();

        // ── Living stage ──
        stage.tickDomain();
        ribbons.tickDomain();
        if (cfg.projectorBeam && due(11, t, 1.0 / 12.0)) Projector.beam(this, stage.pupilLocation(), frame());
        if (due(5, t, 0.25)) stage.ambient();

        // ── Naoya: unnaturally fast, leaves film frames behind ──
        tickCasterAfterimages();

        // ── Atmosphere ──
        if (due(6, t, 0.25)) sendCasterHud(t);
        if (due(7, t, 0.5)) refreshSkyPlayers();
        if (due(8, t, 0.5)) floor.ambience();
        if (due(9, t, 0.5)) world.playSound(origin, Sound.BLOCK_COMPARATOR_CLICK, 0.35f, 0.7f);
        if (due(10, t, 6.0)) {
            world.playSound(origin, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.4f, 0.6f);
            world.playSound(origin, Sound.BLOCK_CHAIN_PLACE, 1.0f, 0.5f);
        }
    }

    /** One dark frame as the barrier seals: the cut. Blindness fades back in over the last second. */
    private void cutToBlack() {
        if (!cfg.cutToBlack) return;
        for (UUID id : skyPlayers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline() && pl.getWorld() == world() && !pl.equals(caster)) {
                pl.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 22, 0, false, false, false));
                blindness.grant(id);
            }
        }
    }

    private void tickScan(double t) {
        double sp = MathUtil.clamp(t / SCAN_SECONDS, 0.0, 1.0);
        double eased = MathUtil.smoothstep(sp);
        double R = getRadius();
        double lineZ = (-R * 0.6 - 2.0) + (R - (-R * 0.6 - 2.0)) * eased;
        Projector.scanLine(this, lineZ, frame());
        rule.scanTo(lineZ);
        if (sp >= 1.0) {
            scanDone = true;
            rule.finishScan();
        }
    }

    private void applyCasterBuffs() {
        if (!cfg.casterBuffs || !caster.isOnline()) return;
        int ticks = (int) Math.round((cfg.domainSeconds + cfg.collapseSeconds) * 20);
        // Never downgrade an existing Speed II+
        PotionEffect existing = caster.getPotionEffect(PotionEffectType.SPEED);
        if (existing == null || existing.getAmplifier() < 1 || existing.getDuration() < ticks) {
            caster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, 1, false, false, true));
            speedGranted = true;
        }
    }

    private void tickCasterAfterimages() {
        Location now = caster.getLocation();
        if (casterLast != null && casterLast.getWorld().equals(now.getWorld())
                && casterLast.distanceSquared(now) > 0.04) {
            Film.afterimage(now.getWorld(), casterLast, frame());
        }
        casterLast = now;
    }

    private void sendCasterHud(double t) {
        double remaining = Math.max(0.0, cfg.domainSeconds - t);
        int total = 24; // one bar per frame of a second of film
        int filled = (int) Math.min(total, Math.max(0, Math.round((t / cfg.domainSeconds) * total)));
        caster.sendActionBar(MM.deserialize("<dark_gray>[ <#d8d8e8>" + "▰".repeat(filled)
                + "<gray>" + "▱".repeat(total - filled)
                + " <dark_gray>] <#d8d8e8>Time Cell Moon Palace <white>" + (int) Math.ceil(remaining)
                + "s <dark_gray>| <gray>FRAME <white>" + String.format("%05d", frame())));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // COLLAPSE
    // ═════════════════════════════════════════════════════════════════════════

    private void tickCollapse(double t) {
        World world = world();
        if (world == null) return;

        if (!collapseStarted) {
            collapseStarted = true;
            cleanupVerticalHandling();
            rule.end(); // everyone thaws, frames shatter, mobs get their AI back
            DomainExitController.returnCaptives(this);
            world.playSound(origin, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.6f);
            world.playSound(origin, Sound.BLOCK_CHAIN_PLACE, 1.5f, 0.5f);
            caster.showTitle(Title.title(
                    Component.empty(),
                    MM.deserialize("<gray><italic>The film runs out...."),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1600), Duration.ofMillis(600))));
        }

        stage.tickCollapse(t, cfg.collapseSeconds);
        ribbons.tickCollapse(t);

        double p = MathUtil.smoothstep(t / cfg.collapseSeconds);
        // The reverse of the formation: the front walks back down, so the crown
        // breaks first and the studio floor is the last thing to go.
        blackout.setFormationProgress(FormationProgress.continuous(1.0 - p));

        // The floor sinks back into the world from the rim inwards, after the
        // walls are gone.
        double floorPhase = MathUtil.clamp(
                (p - (1.0 - Barrier.FLOOR_LEAD_IN)) / Barrier.FLOOR_LEAD_IN, 0.0, 1.0);
        floor.collapse(floorPhase);
        blackout.setFloorFrontRadius(Math.max(0.0,
                Math.min(floor.builtRadius(), getRadius()) * (1.0 - floorPhase)));

        double skyP = MathUtil.clamp((t - 0.3) / Math.max(0.1, cfg.collapseSeconds - 0.3), 0.0, 1.0);
        for (UUID id : skyPlayers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) Sky.fadeOut(pl, skyP);
        }

        // the last frame: one blown-out flash as the real world comes back
        if (!endFlash && t >= cfg.collapseSeconds - 0.25) {
            endFlash = true;
            Projector.flash(origin.clone().add(0, 2.0, 0));
            world.playSound(origin, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1.6f, 0.5f);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Ends the domain with the outro that matches the reason.
     *
     * <p>A caster that dies inside the time cell does not get a tidy collapse:
     * the barrier shatters in fracture fronts and every viewer only receives the
     * blocks they actually saw. Every other ending (expiry, quit, shutdown)
     * keeps the reverse top-down collapse.</p>
     */
    @Override
    public void end(SessionEndReason reason) {
        if (!isActive()) return;
        if (reason == SessionEndReason.CASTER_DEATH) {
            // Stop enforcing the timeline now; the presentation owns the shell
            // and studio floor visuals until they finish.
            if (task != null && !task.isCancelled()) task.cancel();
            collapseStarted = true;
            cleanupVerticalHandling();
            HandlerList.unregisterAll(enforcer);
            rule.end();
            DomainExitController.returnCaptives(this);

            shatterAnimator = new BarrierShatterAnimator(
                    getCenter(), getRadius(),
                    Material.BLACK_CONCRETE.createBlockData(),
                    floor.surfaceY());
            if (!shatterAnimator.play(
                    blackout::shatter,
                    () -> floor.platform() != null && floor.platform().shatter(),
                    this::finishShatter)) {
                shatterAnimator = null;
                close();
                return;
            }
            // Keep session active for animator callbacks/finalization.
            return;
        }
        close();
    }

    /** Called by the shatter presentation once its visuals are done. */
    private void finishShatter() {
        shatterAnimator = null;
        if (blackout != null) blackout.close();
        if (floor != null) floor.close();
        // Safety net: if the session is still active, finish remaining cleanup.
        if (isActive()) {
            finalizeCloseState();
        }
    }

    @Override
    public void close() {
        if (!isActive()) return;

        if (task != null && !task.isCancelled()) task.cancel();

        if (shatterAnimator == null) {
            cleanupVerticalHandling();
            HandlerList.unregisterAll(enforcer);
            rule.end();
            DomainExitController.returnCaptives(this);
        }

        finalizeCloseState();
    }

    @Override
    public void closeForShutdown() {
        // disable() cancels the scheduler immediately after stopAll(), so the
        // shatter's finish callback would never run. Stop the presentation now and
        // clear the reference so finalizeCloseState() closes the real floor and
        // blackout synchronously.
        if (shatterAnimator != null) {
            shatterAnimator.close();
            shatterAnimator = null;
        }
        close();
    }

    private void finalizeCloseState() {
        if (!isActive()) return;

        // Ensure enforcement is stopped and captives returned if not already.
        try {
            HandlerList.unregisterAll(enforcer);
        } catch (Throwable ignored) {
            // intentional: unregisterAll on an already-unregistered handler is a no-op failure.
        }
        rule.end();
        cleanupVerticalHandling();

        // Take back only the Speed we granted
        if (speedGranted && caster.isOnline()) {
            PotionEffect remaining = caster.getPotionEffect(PotionEffectType.SPEED);
            if (remaining != null && remaining.getAmplifier() == 1) {
                caster.removePotionEffect(PotionEffectType.SPEED);
            }
        }

        // Everyone gets their sky back, and the cut-to-black blindness taken off
        // exactly the players we blinded (never a stranger's).
        for (UUID id : skyPlayers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) Sky.reset(p);
        }
        for (UUID id : blindness.playersToClear()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) p.removePotionEffect(PotionEffectType.BLINDNESS);
        }
        blindness.clear();
        skyPlayers.clear();

        // Remove every display
        stage.clear();
        ribbons.clear();
        for (Entity e : spawnedDisplays) {
            if (e != null && e.isValid()) e.remove();
        }
        spawnedDisplays.clear();

        // During a shatter presentation the shell and floor belong to the animator until it finishes.
        if (shatterAnimator == null) {
            if (blackout != null) blackout.close();
            if (floor != null) floor.close();
        }

        deactivate();
        ShenanigansCore.getInstance().getSessionManager().removeSession(this);
    }
}
