package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.display.TitleAnimator;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import com.hizeric.shenaniganscore.core.session.SessionEndReason;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The whole Womb Profusion sequence, its state and its cleanup.
 *
 * <pre>
 *  SIGN      1.4s  the typed title; spores gather; the ground trembles. The tower is planned and its
 *                  rigs are built as invisible specks, one per tick.
 *  MANIFEST  6.0s  the ground turns to flesh in a spreading ring, the mandala drawing itself in;
 *                  the sky sickens; at 1.2s the tower erupts tier by tier, whispering. Everyone
 *                  inside is warned: the edge of the carpet is the edge of the blast
 *  CHARGE    2.2s  the uzumaki: spirits spiral into the tower's core, the orb swells, the
 *                  heartbeat quickens
 *  BLAST     1.4s  the orb bursts; a spiral shockwave crosses the area and hits everyone inside
 *  DISPEL    3.2s  the carpet recedes from the edge inward, the tower crumbles, the sky clears
 * </pre>
 *
 * <p>An <b>open</b> domain: there is no shell. The domain's centre is the tower's foot, and the caster
 * stands a little in front of it, so victims see him framed against it.</p>
 *
 * Config ({@code jujutsukaisen.wombprofusion.*}): {@code quality}, {@code manifest-seconds},
 * {@code charge-seconds}, {@code blast.damage-percent}, {@code blast.lethal}, {@code blast.knockback}.
 */
public class Session extends DomainSession {


    private static final double SIGN_SECONDS = 1.4;
    private static final double DEFAULT_MANIFEST = 6.0;
    private static final double DEFAULT_CHARGE = 2.2;
    private static final double BLAST_SECONDS = 1.4;
    private static final double DISPEL_SECONDS = 3.2;
    /** When the carpet has finished spreading, and when the tower starts to erupt, within MANIFEST. */
    private static final double CARPET_SECONDS = 3.5;
    private static final double ERUPT_AT = 1.2;

    private static final String DOMAIN_TITLE = "領域展開";
    private static final String NAME_TITLE = "胎蔵遍野";

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final Player caster;
    private final long startTime;
    private final double manifestSeconds;
    private final double chargeSeconds;
    private final double totalSeconds;
    private final double quality;
    private final double damagePercent;
    private final boolean lethal;
    private final double knockback;

    public final List<Entity> spawnedDisplays = new ArrayList<>();

    private final Tower tower;
    private final Carpet carpet;
    private final Veins veins;
    private CoreOrb orb;
    private Spirits spirits;
    private Blast blast;

    private final Set<UUID> skyPlayers = new HashSet<>();
    private long skyFrom;
    private final BukkitRunnable task;

    private final int[] lastStep = new int[16];
    private int phase = -1;
    private boolean towerBuilt;
    private boolean veinsBuilt;
    private boolean erupted;
    private boolean chargeStarted;
    private boolean blastStarted;
    private boolean dispelStarted;
    private boolean skyStarted;

    public Session(Player caster, double radius) {
        super(caster, centerFor(caster, radius), radius);
        this.caster = caster;
        this.startTime = System.currentTimeMillis();
        Arrays.fill(lastStep, -1);

        // All gameplay values are hardcoded (config.yml is a stub). See INTEGRATION.md for the
        // documented defaults; these were the fallback values of the old config reads.
        this.quality = 1.0;
        this.manifestSeconds = DEFAULT_MANIFEST;
        this.chargeSeconds = DEFAULT_CHARGE;
        this.damagePercent = 65.0;
        this.lethal = false;
        this.knockback = 1.6;

        this.totalSeconds = SIGN_SECONDS + manifestSeconds + chargeSeconds + BLAST_SECONDS + DISPEL_SECONDS;

        long seed = caster.getUniqueId().getLeastSignificantBits() ^ System.nanoTime();
        this.tower = new Tower(getCenter(), radius, quality, seed, spawnedDisplays);
        this.carpet = new Carpet(getCenter(), radius, seed);
        this.veins = new Veins(getCenter(), radius, quality, seed, spawnedDisplays);

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
                    double manifestEnd = SIGN_SECONDS + manifestSeconds;
                    double chargeEnd = manifestEnd + chargeSeconds;
                    double blastEnd = chargeEnd + BLAST_SECONDS;

                    if (elapsed < SIGN_SECONDS) {
                        enterPhase(0);
                        tickSign(elapsed);
                    } else if (elapsed < manifestEnd) {
                        enterPhase(1);
                        tickManifest(elapsed - SIGN_SECONDS);
                    } else if (elapsed < chargeEnd) {
                        enterPhase(2);
                        tickCharge(elapsed - manifestEnd);
                    } else if (elapsed < blastEnd) {
                        enterPhase(3);
                        tickBlast(elapsed - chargeEnd);
                    } else {
                        enterPhase(4);
                        tickDispel(elapsed - blastEnd);
                    }

                    carpet.tick();
                    tower.tick();
                } catch (Throwable t) {
                    ShenanigansCore.getInstance().getLogger()
                            .warning("Womb Profusion tick failed, closing domain: " + t);
                    cancel();
                    close();
                }
            }
        };
    }

    /** The domain's centre is the tower's foot, a little behind the caster, on the ground. */
    private static Location centerFor(Player caster, double radius) {
        Vector look = caster.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1.0e-4) look = new Vector(0, 0, 1);
        Location c = caster.getLocation().clone().subtract(look.normalize().multiply(0.3 * radius));
        World w = c.getWorld();
        if (w != null) c.setY(w.getHighestBlockYAt(c.getBlockX(), c.getBlockZ(), HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1.0);
        return c;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void start() {
        task.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    public Player casterPlayer() {
        return caster;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void enterPhase(int p) {
        if (phase == p) return;
        phase = p;
        Arrays.fill(lastStep, -1);
    }

    /** True once per {@code interval} seconds of {@code t}. Use a fixed interval per slot. */
    private boolean due(int slot, double t, double interval) {
        if (t < 0) return false;
        int step = (int) (t / interval);
        if (step > lastStep[slot]) {
            lastStep[slot] = step;
            return true;
        }
        return false;
    }

    private static double smooth(double p) {
        p = Math.max(0.0, Math.min(1.0, p));
        return p * p * (3.0 - 2.0 * p);
    }

    /** The caster and every player inside the radius. */
    private List<Player> inside() {
        List<Player> out = new ArrayList<>();
        World world = getCenter().getWorld();
        if (world == null) return out;
        double r2 = getRadius() * getRadius();
        for (Player p : world.getPlayers()) {
            if (p.equals(caster) || p.getLocation().distanceSquared(getCenter()) <= r2) out.add(p);
        }
        return out;
    }

    private void refreshSkyPlayers() {
        World world = getCenter().getWorld();
        if (world == null) return;
        double r2 = (getRadius() + 12) * (getRadius() + 12);
        for (Player p : world.getPlayers()) {
            boolean in = p.getLocation().distanceSquared(getCenter()) <= r2;
            if (in) {
                if (skyPlayers.add(p.getUniqueId()) && skyStarted) Sky.hold(p);
            } else if (skyPlayers.remove(p.getUniqueId())) {
                Sky.reset(p);
            }
        }
    }

    private void forSky(java.util.function.Consumer<Player> action) {
        World world = getCenter().getWorld();
        for (UUID id : skyPlayers) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            // Never apply sky effects to a player who left the domain world (F-45).
            if (p != null && p.isOnline() && world != null && p.getWorld() == world) action.accept(p);
        }
    }

    /** A ring of dust at the carpet's leading edge, drawn on the ground. */
    private void edgeRing(World world, double ring, Particle.DustOptions dust) {
        if (ring < 1.5) return;
        int points = (int) Math.min(90, Math.max(24, ring * 2.2));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2.0 * i / points;
            double x = getCenter().getX() + Math.cos(a) * ring;
            double z = getCenter().getZ() + Math.sin(a) * ring;
            double y = world.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z),
                    HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1.15;
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.1, 0.05, 0.1, 0, dust);
        }
    }

    private static final Particle.DustOptions EDGE_RED = new Particle.DustOptions(Color.fromRGB(170, 20, 50), 1.5f);
    private static final Particle.DustOptions EDGE_VIOLET = new Particle.DustOptions(Color.fromRGB(180, 90, 255), 1.4f);

    // ------------------------------------------------------------------
    // SIGN
    // ------------------------------------------------------------------

    private void tickSign(double elapsed) {
        World world = getCenter().getWorld();
        if (world == null) return;

        if (due(0, elapsed, 0.05) && !carpet.isPrepared()) carpet.prepareStep();
        if (!towerBuilt) towerBuilt = tower.buildStep();
        else if (!veinsBuilt) veinsBuilt = veins.buildStep();

        if (due(1, elapsed, 0.15)) {
            boolean last = elapsed >= SIGN_SECONDS - 0.25;
            Title title = TitleAnimator.build(DOMAIN_TITLE, NAME_TITLE, "Womb Profusion", elapsed, last);
            for (Player p : inside()) p.showTitle(title);
        }

        if (due(2, elapsed, 0.7)) {
            world.playSound(getCenter(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.5f);
            world.playSound(getCenter(), Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.5f, 0.5f);
        }
        // Spores gather, and the earth shivers.
        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, getCenter().clone().add(0, 1.5, 0), 6, 4, 1.5, 4, 0);
        world.spawnParticle(Particle.ASH, getCenter().clone().add(0, 1.0, 0), 4, 5, 1.0, 5, 0);
    }

    // ------------------------------------------------------------------
    // MANIFEST
    // ------------------------------------------------------------------

    private void tickManifest(double t) {
        World world = getCenter().getWorld();
        if (world == null) return;

        // Make sure the build finished even if the sign phase was short.
        if (!carpet.isPrepared()) carpet.prepareStep();
        if (!towerBuilt) towerBuilt = tower.buildStep();
        else if (!veinsBuilt) veinsBuilt = veins.buildStep();

        if (!skyStarted) {
            skyStarted = true;
            skyFrom = world.getTime();
            refreshSkyPlayers();
            world.playSound(getCenter(), Sound.ENTITY_WARDEN_EMERGE, 2.0f, 0.4f);
            world.playSound(getCenter(), Sound.AMBIENT_CRIMSON_FOREST_MOOD, 1.5f, 0.5f);
        }

        // The carpet spreads in a ring, with the leading edge picked out in dust.
        double carpetP = smooth(t / CARPET_SECONDS);
        carpet.setTarget(carpetP);
        veins.grow(smooth((t - 0.3) / (CARPET_SECONDS + 0.5)));
        if (carpetP > 0.0 && carpetP < 1.0) {
            edgeRing(world, carpet.reachedRadius(), (tickParity() ? EDGE_RED : EDGE_VIOLET));
        }

        // The sky sickens.
        if (due(0, t, 0.5)) refreshSkyPlayers();
        double skyP = Math.min(1.0, t / 4.0);
        forSky(p -> Sky.fadeIn(p, skyFrom, skyP));

        // The tower erupts, tier by tier.
        if (!erupted && t >= ERUPT_AT && towerBuilt) {
            erupted = true;
            tower.erupt();
            world.playSound(getCenter(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.4f);
        }

        // Whispers from the faces.
        if (due(1, t, 1.6)) {
            Location at = tower.coreLocation();
            world.playSound(at, Sound.ENTITY_GHAST_AMBIENT, 1.4f, 0.5f);
            world.playSound(at, Sound.ENTITY_ZOMBIE_VILLAGER_AMBIENT, 1.2f, 0.55f);
        }

        // Warn everyone inside: the carpet's edge is the blast's edge.
        if (t > 2.0 && due(2, t, 1.0)) {
            for (Player p : inside()) {
                if (p.equals(caster)) continue;
                p.sendActionBar(MM.deserialize("<dark_red>The ground is alive. <gray>Leave the carpet: <white>its edge is the edge of the blast."));
            }
        }
        if (due(3, t, 0.35)) sendCasterHud("MANIFEST", t / manifestSeconds);
    }

    private int parity;

    private boolean tickParity() {
        return (parity++ & 1) == 0;
    }

    // ------------------------------------------------------------------
    // CHARGE
    // ------------------------------------------------------------------

    private void tickCharge(double t) {
        World world = getCenter().getWorld();
        if (world == null) return;

        carpet.setTarget(1.0);
        if (due(3, t, 0.5)) forSky(Sky::hold);

        if (!chargeStarted) {
            chargeStarted = true;
            int ticks = (int) Math.round(chargeSeconds * 20.0);
            Location core = tower.coreLocation();
            orb = new CoreOrb(core, getRadius() * 0.07, spawnedDisplays);
            orb.grow(ticks);
            spirits = new Spirits(core, getRadius(), quality, spawnedDisplays);
            spirits.start(ticks);
            tower.scream(ticks);
            // The blast is built now (as invisible specks) so firing it costs nothing.
            blast = new Blast(core, getCenter(), getRadius(), caster, damagePercent, lethal, knockback,
                    quality, spawnedDisplays);
            world.playSound(core, Sound.BLOCK_BEACON_AMBIENT, 2.0f, 0.5f);
            world.playSound(core, Sound.ENTITY_PHANTOM_AMBIENT, 1.6f, 0.5f);
        }

        if (spirits != null) spirits.tick();
        if (orb != null) orb.tick();

        // The heartbeat quickens as the charge completes.
        double p = Math.min(1.0, t / chargeSeconds);
        if (due(0, t, Math.max(0.2, 0.6 - 0.35 * p) )) {
            world.playSound(tower.coreLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, (float) (0.5 + 0.5 * p));
        }

        if (due(1, t, 0.25)) {
            double left = Math.max(0.0, chargeSeconds - t);
            for (Player pl : inside()) {
                if (pl.equals(caster)) continue;
                pl.sendActionBar(MM.deserialize("<dark_red><bold>THE WOMB OPENS <white>" + String.format("%.1f", left)
                        + "s <gray>| leave the carpet"));
            }
        }
        if (due(2, t, 0.35)) sendCasterHud("CHARGE", p);
    }

    // ------------------------------------------------------------------
    // BLAST
    // ------------------------------------------------------------------

    private void tickBlast(double t) {
        World world = getCenter().getWorld();
        if (world == null) return;
        if (due(1, t, 0.5)) forSky(Sky::hold);

        if (!blastStarted) {
            blastStarted = true;
            if (spirits != null) spirits.close();
            if (orb != null) orb.release();
            if (blast != null) blast.start();
            if (caster.isOnline()) {
                caster.showTitle(Title.title(net.kyori.adventure.text.Component.empty(),
                        MM.deserialize("<dark_red><italic>胎蔵遍野"),
                        Title.Times.times(java.time.Duration.ofMillis(60), java.time.Duration.ofMillis(900),
                                java.time.Duration.ofMillis(300))));
            }
        }
        if (blast != null) blast.tick();
        if (due(0, t, 0.35)) sendCasterHud("BLAST", Math.min(1.0, t / BLAST_SECONDS));
    }

    // ------------------------------------------------------------------
    // DISPEL
    // ------------------------------------------------------------------

    private void tickDispel(double t) {
        World world = getCenter().getWorld();
        if (world == null) return;

        if (!dispelStarted) {
            dispelStarted = true;
            if (orb != null) orb.fade(20);
            tower.collapse(48);
            veins.collapse(40);
            world.playSound(getCenter(), Sound.BLOCK_SLIME_BLOCK_BREAK, 2.0f, 0.4f);
            world.playSound(getCenter(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.6f, 0.5f);
        }

        // The carpet recedes from the edge inward: the domain is dispelled from its edge.
        double p = smooth(t / (DISPEL_SECONDS * 0.9));
        carpet.setTarget(1.0 - p);
        if (p > 0.0 && p < 1.0) edgeRing(world, carpet.reachedRadius(), EDGE_VIOLET);

        // The sky clears.
        double skyP = Math.min(1.0, t / (DISPEL_SECONDS * 0.9));
        forSky(pl -> Sky.fadeOut(pl, skyP));

        // Crumbling flesh falls around the tower's foot.
        world.spawnParticle(Particle.BLOCK, getCenter().clone().add(0, 4, 0), 14, getRadius() * 0.14, 3.0,
                getRadius() * 0.14, 0.05, Material.PINK_TERRACOTTA.createBlockData());
        if (due(0, t, 0.6)) world.playSound(getCenter(), Sound.BLOCK_SLIME_BLOCK_BREAK, 1.2f, 0.5f);
        if (due(1, t, 0.35)) sendCasterHud("DISPEL", p);
    }

    // ------------------------------------------------------------------
    // HUD
    // ------------------------------------------------------------------

    private void sendCasterHud(String label, double p) {
        if (!caster.isOnline()) return;
        int bars = 24;
        int filled = Math.max(0, Math.min(bars, (int) Math.round(p * bars)));
        caster.sendActionBar(MM.deserialize("<dark_red>" + "▰".repeat(filled) + "<dark_gray>" + "▱".repeat(bars - filled)
                + " <#d8cfc0>胎蔵遍野 <gray>" + label));
    }

    // ------------------------------------------------------------------
    // Cleanup
    // ------------------------------------------------------------------

    /** An open domain has no shell to shatter: any ending simply lets go. */
    @Override
    public void end(SessionEndReason reason) {
        close();
    }

    @Override
    public void close() {
        if (!isActive()) return;
        if (task != null && !task.isCancelled()) task.cancel();

        if (blast != null) blast.close();
        if (spirits != null) spirits.close();
        if (orb != null) orb.close();
        tower.close();
        veins.close();

        for (UUID id : skyPlayers) {
            Player p = org.bukkit.Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) Sky.reset(p);
        }
        skyPlayers.clear();

        // Hands the real ground back to every viewer.
        carpet.close();

        for (Entity e : spawnedDisplays) {
            if (e != null && e.isValid()) e.remove();
        }
        spawnedDisplays.clear();

        deactivate();
        ShenanigansCore.getInstance().getSessionManager().removeSession(this);
    }
}
