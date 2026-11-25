package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import com.hizeric.shenaniganscore.core.display.TitleAnimator;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.session.DomainExitController;
import com.hizeric.shenaniganscore.core.session.DomainSession;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Malevolent Shrine timeline.
 *
 * <pre>
 *  SIGN       1.5s  bell, title, cursed-energy spiral around the caster
 *  AWAKENING  4.5s  veins crack outward, the tide floods the ground, the sky time-lapses to
 *                   blood-red dusk, the shrine erupts wave by wave
 *  DOMAIN    16.0s  Cleave + Dismantle, crescent waves, living shrine (chewing maws, swaying
 *                   skulls, spinning heart), ripples, mist, ash, periodic ground rumble
 *                   (decaying camera shake + crust dust + a tiny grounded hop)
 *  COLLAPSE   5.5s  tremor, the shrine dismantles top-down in tumbling waves, the tide recedes
 *                   as a burning rim, the sky rolls back to the real time
 *                   (viewers are eased back out of the camera shake in close())
 * </pre>
 */
public class Session extends DomainSession {
    private final Player caster;
    private final long startTime;
    private final Location shrineCenter;
    private final float casterYaw;

    // ── Timeline (seconds) — hardcoded to the shipped values ──
    // (buffed: longer, more cinematic animations by default; damage/radius unchanged)
    private static final double DEFAULT_SIGN_SECONDS = 1.5;
    private static final double DEFAULT_AWAKEN_SECONDS = 4.5;
    private static final double DEFAULT_DOMAIN_SECONDS = 16.0;
    private static final double DEFAULT_COLLAPSE_SECONDS = 5.5;

    private static final double WAVE_START = 0.4;
    private static final double DEFAULT_WAVE_GAP = 0.38;

    /** The animated domain-expansion title (see {@link TitleAnimator}). */
    private static final String DOMAIN_TITLE = "領域展開";
    /** The shrine's own name: 伏魔御厨 — "the demon shrine". */
    private static final String SHRINE_TITLE = "伏魔御厨";

    private final double signSeconds;
    private final double awakenSeconds;
    private final double domainSeconds;
    private final double collapseSeconds;
    private final double waveGap;
    private final double totalSeconds;
    /** Seconds between Cleave pulses (config: cleave.interval-seconds). */
    private final double cleaveInterval;
    private final boolean groundThump;
    private final boolean casterBuffsEnabled;
    private final boolean rumbleEnabled;
    private final double rumbleIntensity;

    // ── Shared state (used by Builder / Floor / Dismantle / Cleave) ──
    public final List<Entity> spawnedDisplays = new ArrayList<>();
    /** Legacy: Floor now owns its own fake-block bookkeeping. Kept so old callers still compile. */
    public final List<Location> changedBlocks = new ArrayList<>();
    /** The real blocks the shrine's terrace is made of, and what was under them. */
    public final Map<Location, BlockData> physicalBlocks = new LinkedHashMap<>();
    public final List<Builder.Piece> pieces = new ArrayList<>();
    public final List<Builder.JawPart> jaws = new ArrayList<>();
    public final List<Builder.HangingSkull> hanging = new ArrayList<>();
    public final List<BlockDisplay> wisps = new ArrayList<>();
    private BlockDisplay shrineCore;
    private BlockDisplay shrineCrest;
    public final Floor floor = new Floor(this);
    public int liveWaves = 0;
    public final Set<UUID> warned = new HashSet<>();
    /** Cleave escalation per victim (RAVAGE level). Cleared on close. */
    public final Map<UUID, Integer> ravageCounts = new ConcurrentHashMap<>();
    /** Slash displays spawned for this domain; disposed on every close path. */
    public final SlashDisplayRegistry slashDisplays = new SlashDisplayRegistry();
    /** Enemy projectiles/TNT stalked by Dismantle before the delayed final cut. */
    public final Map<UUID, Dismantle.TrackedCut> trackedProjectileCuts = new ConcurrentHashMap<>();

    /** The glowing core display and the crest that caps the shrine; written and read by Builder. */
    BlockDisplay getShrineCore() { return shrineCore; }
    void setShrineCore(BlockDisplay core) { this.shrineCore = core; }
    BlockDisplay getShrineCrest() { return shrineCrest; }
    void setShrineCrest(BlockDisplay crest) { this.shrineCrest = crest; }

    /** Ground-rumble camera shakes, keyed by victim. State lives here, never in globals. */
    private final Map<UUID, Shake> shakes = new ConcurrentHashMap<>();

    private volatile double domainElapsed = 0.0;
    private volatile double biteUntil = 0.0;

    private final Set<UUID> skyPlayers = new HashSet<>();
    private long skyStartTime = 0L;

    private static final Particle.DustOptions BLOOD_DUST =
            new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.2f);
    private static final Particle.DustTransition EDGE_DUST =
            new Particle.DustTransition(Color.fromRGB(175, 0, 0), Color.fromRGB(20, 0, 0), 1.6f);
    private static final BlockData DEBRIS = org.bukkit.Material.BLACKSTONE.createBlockData();

    private final BukkitRunnable task;

    // ── Phase state ──
    private int phase = -1;
    private boolean signShown = false;
    private boolean awakenInit = false;
    private boolean domainInit = false;
    private boolean buffsApplied = false;
    /** True only when this domain granted the caster's Resistance (so close() removes only that). */
    private boolean grantedResistance = false;
    private boolean collapseStarted = false;
    private int currentWave = 0;
    private int collapsedWaves = 0;

    /** Step counters: one slot per periodic effect; reset on every phase change. */
    private final int[] lastStep = new int[24];
    private final List<Double> ripples = new ArrayList<>();

    /** A viewer rotation sampled from a decaying shake at a point in time. */
    record Rotation(float yaw, float pitch) { }

    /** One active camera shake for a viewer. Oscillates around a fixed base rotation and decays. */
    static final class Shake {
        final float baseYaw;
        final float basePitch;
        final double amplitude;   // degrees at t = 0
        final long startMs;
        final long durationMs;

        Shake(float baseYaw, float basePitch, double amplitude, long durationMs) {
            this(baseYaw, basePitch, amplitude, System.currentTimeMillis(), durationMs);
        }

        /** Explicit clock variant: lets the curve be tested without waiting on a real timer. */
        Shake(float baseYaw, float basePitch, double amplitude, long startMs, long durationMs) {
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
            this.amplitude = amplitude;
            this.startMs = startMs;
            this.durationMs = Math.max(1L, durationMs);
        }

        /**
         * Combines an already-registered shake with a new one without losing the
         * original base. The newest registration drives amplitude, start and
         * duration; an already-expired active shake contributes nothing, so the
         * incoming base wins in that case.
         */
        static Shake layer(Shake active, Shake incoming) {
            if (active == null) return incoming;
            long elapsedSinceActiveStart = incoming.startMs - active.startMs;
            if (elapsedSinceActiveStart >= active.durationMs) return incoming;
            return new Shake(active.baseYaw, active.basePitch, incoming.amplitude,
                    incoming.startMs, incoming.durationMs);
        }

        /**
         * Rotation at {@code now}: a decaying sine around the recorded base,
         * landing exactly back on that base once the shake has expired. Pitch
         * is clamped to the legal ±89° range.
         */
        Rotation rotationAt(long now) {
            long elapsed = Math.max(0L, now - startMs);
            if (elapsed >= durationMs) {
                return new Rotation(baseYaw, basePitch);
            }
            double progress = elapsed / (double) durationMs;
            double decay = Math.pow(1.0 - progress, 1.5); // hard at the start, feathered at the end
            double yawOffset = Math.sin(elapsed * 0.021) * amplitude * decay;
            double pitchOffset = Math.sin(elapsed * 0.026 + 1.4) * amplitude * 0.65 * decay;
            float pitch = (float) Math.max(-89.0, Math.min(89.0, basePitch + pitchOffset));
            return new Rotation(baseYaw + (float) yawOffset, pitch);
        }
    }

    public Session(Player caster, double radius) {
        super(caster, caster.getLocation(), radius);
        this.caster = caster;
        this.startTime = System.currentTimeMillis();
        this.casterYaw = caster.getLocation().getYaw();
        Arrays.fill(lastStep, -1);

        String p = "jujutsukaisen.malevolentshrine";
        this.signSeconds = DEFAULT_SIGN_SECONDS;
        this.awakenSeconds = DEFAULT_AWAKEN_SECONDS;
        this.domainSeconds = DEFAULT_DOMAIN_SECONDS;
        this.collapseSeconds = DEFAULT_COLLAPSE_SECONDS;
        this.waveGap = DEFAULT_WAVE_GAP;
        double rawInterval = 1.0;
        this.cleaveInterval = Math.max(0.25, Math.min(5.0, rawInterval));
        this.totalSeconds = signSeconds + awakenSeconds + domainSeconds + collapseSeconds;
        this.groundThump = true;
        this.casterBuffsEnabled = true;
        this.rumbleEnabled = true;
        double rawIntensity = 1.0;
        this.rumbleIntensity = Math.max(0.0, Math.min(3.0, rawIntensity));

        // Shrine spawns 10 blocks behind the caster
        Vector direction = caster.getLocation().getDirection().setY(0).normalize();
        this.shrineCenter = caster.getLocation().clone().subtract(direction.multiply(10));

        // The shrine stands on a flat platform, so the temple's datum is the
        // caster's footing — not whatever the terrain happens to do under the
        // shrine. The platform's surface sits one block below this.
        this.shrineCenter.setY(caster.getLocation().getBlockY() + 1.0);

        this.task = new BukkitRunnable() {
            @Override
            public void run() {
                // Match the Unlimited Void / Time Cell guard: a single failing
                // tick must not leak. Without this, an exception aborts the tick,
                // floods the scheduler log, and can leave the session registered
                // forever — blocking every future technique cast via the domain
                // overlap lock.
                try {
                    double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;

                    if (elapsed >= totalSeconds) {
                        this.cancel();
                        close();
                        return;
                    }

                    double t = elapsed;
                    if (t <= signSeconds) {
                        enterPhase(0);
                        tickSign(t);
                    } else if (t <= signSeconds + awakenSeconds) {
                        enterPhase(1);
                        tickAwaken(t - signSeconds);
                    } else if (t <= signSeconds + awakenSeconds + domainSeconds) {
                        enterPhase(2);
                        tickDomain(t - signSeconds - awakenSeconds);
                    } else {
                        enterPhase(3);
                        tickCollapse(t - signSeconds - awakenSeconds - domainSeconds);
                    }

                    floor.tick();
                    tickVerticalHandling();
                    applyShakes();
                } catch (Throwable failure) {
                    ShenanigansCore.getInstance().getLogger()
                            .warning("Malevolent Shrine tick failed, closing domain: " + failure);
                    this.cancel();
                    close();
                }
            }
        };
    }
    public void start() {
        this.task.runTaskTimer(ShenanigansCore.getInstance(), 0L, 1L);
    }

    // ── Small accessors ──
    public Location getShrineCenter() { return shrineCenter; }
    public float getCasterYaw() { return casterYaw; }
    public int getCurrentWave() { return currentWave; }
    public double getBiteUntil() { return biteUntil; }
    public double getDomainElapsed() { return domainElapsed; }
    public Player casterPlayer() { return caster; }

    /** Cleave calls this: the jaws snap shut for a moment. */
    public void bite() { biteUntil = domainElapsed + 0.35; }

    /**
     * Remembers the block that was under a piece of the shrine's real terrace,
     * so the world can be handed back when the domain ends.
     *
     * <p>The block is also claimed in {@link DomainRegistry} so the
     * {@link DomainRegionGuard} will deny any external mutation of it while
     * the domain is active. The claim is released when the block is restored.</p>
     */
    public void rememberPhysicalBlock(Location location, BlockData original) {
        if (location == null || original == null) return;
        physicalBlocks.putIfAbsent(location.clone(), original);
        DomainRegistry.get().claim(getUniqueId(), location.getBlock());
    }

    /**
     * Carves a cut into a block the shrine owns (Dismantle's scars).
     *
     * <p>Domain-internal mutations go through this method rather than a raw
     * {@code setBlockData} so the registry stays consistent and the guard
     * never sees a legitimate domain write as an external attack.</p>
     */
    public void mutatePhysicalBlock(Location location, BlockData data) {
        if (location == null || data == null) return;
        if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        try {
            location.getBlock().setBlockData(data, false);
        } catch (Throwable ignored) {
            // Chunk unloaded: nothing trustworthy to write.
        }
    }

    /** Puts the shrine's real terrace back into the world. */
    private void restorePhysicalBlocks() {
        for (Map.Entry<Location, BlockData> entry : physicalBlocks.entrySet()) {
            Location location = entry.getKey();
            if (location.getWorld() == null) continue;
            if (!location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                continue;
            }
            try {
                location.getBlock().setBlockData(entry.getValue(), false);
                DomainRegistry.get().unclaim(getUniqueId(), location.getBlock());
            } catch (Throwable ignored) {
                // Chunk unloaded: nothing trustworthy to put back.
            }
        }
        physicalBlocks.clear();
    }

    private void enterPhase(int p) {
        if (phase == p) return;
        phase = p;
        Arrays.fill(lastStep, -1);
    }

    /** True once per {@code interval} seconds of {@code t}. Jitter-proof (step counter). */
    private boolean due(int slot, double t, double interval) {
        if (t < 0) return false;
        int step = (int) (t / interval);
        if (step > lastStep[slot]) {
            lastStep[slot] = step;
            return true;
        }
        return false;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // SIGN
    // ═════════════════════════════════════════════════════════════════════════

    private void tickSign(double t) {
        World world = shrineCenter.getWorld();
        if (world == null) return;

        if (!signShown) {
            signShown = true;
            world.playSound(shrineCenter, Sound.BLOCK_BELL_USE, 2.2f, 0.82f);
            world.playSound(caster.getLocation(), Sound.BLOCK_BELL_RESONATE, 1.6f, 0.52f);
            world.playSound(caster.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 0.58f);
            world.playSound(shrineCenter, Sound.ENTITY_WARDEN_EMERGE, 2.4f, 0.4f);
            world.playSound(shrineCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.6f, 0.58f);
            world.playSound(shrineCenter, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.7f);

            // The heavy terrain scan happens here, hidden behind the title
            floor.prepare();
        }

        // The domain-expansion title types itself in, glyph by glyph.
        if (due(22, t, 0.15)) {
            boolean last = t >= signSeconds - 0.25;
            Title title = TitleAnimator.build(DOMAIN_TITLE, SHRINE_TITLE, "Malevolent Shrine", t, last);
            showDomainTitle(world, title);
        }

        if (due(18, t, 0.08)) {
            Location c = caster.getLocation().add(0, 1.0, 0);
            world.spawnParticle(Particle.ENCHANT, c, 10, 0.7, 0.7, 0.7, 0.5);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, c, 6, 0.6, 0.6, 0.6, 0, EDGE_DUST);
        }

        // Cursed-energy spiral rising around the caster
        Location c = caster.getLocation();
        for (int k = 0; k < 3; k++) {
            double ang = t * 9.0 + k * (Math.PI * 2 / 3);
            double y = (t * 2.2 + k * 0.7) % 2.2;
            world.spawnParticle(Particle.DUST, c.getX() + Math.cos(ang) * 0.9, c.getY() + y, c.getZ() + Math.sin(ang) * 0.9,
                    1, 0, 0, 0, 0, BLOOD_DUST);
        }
        world.spawnParticle(Particle.ASH, c, 2, 1.2, 0.1, 1.2, 0);
        if (due(14, t, 0.12)) {
            world.spawnParticle(Particle.ENCHANT, c.clone().add(0, 1.5, 0), 6, 0.7, 0.7, 0.7, 0.7);
        }
    }

    /**
     * Shows the animated domain-expansion title to the caster and to everyone
     * standing inside the domain's radius — the same audience the Time Cell
     * gives it.
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

    // ═════════════════════════════════════════════════════════════════════════
    // AWAKENING: veins, tide, sky time-lapse, shrine eruption
    // ═════════════════════════════════════════════════════════════════════════

    private void tickAwaken(double t) {
        World world = shrineCenter.getWorld();
        if (world == null) return;

        if (!awakenInit) {
            awakenInit = true;
            skyStartTime = world.getTime();
            refreshSkyPlayers();
            world.playSound(shrineCenter, Sound.ENTITY_WARDEN_EMERGE, 3.0f, 0.5f);
        }

        // Sky: sunset time-lapse over the first 3.5s
        double skyP = MathUtil.clamp(t / 3.5, 0.0, 1.0);
        if (due(0, t, 0.5)) refreshSkyPlayers();
        for (UUID id : skyPlayers) {
            Player p = Bukkit.getPlayer(id);
            // Domain sky must not follow a player into another world (F-45).
            if (p != null && p.isOnline() && shrineCenter.getWorld() != null && p.getWorld() == shrineCenter.getWorld()) OpenBarrier.fadeIn(p, skyStartTime, skyP);
        }

        // Floor tide: veins shoot ahead, then the dark floods in behind them
        double p = MathUtil.clamp((t - 0.2) / (awakenSeconds - 0.7), 0.0, 1.0);
        double eased = 1.0 - Math.pow(1.0 - p, 3.0);
        floor.expand(eased);
        if (p > 0.0 && p < 1.0) {
            double front = floor.frontRadius(eased);
            spawnTideRing(world, front);
        }

        // Shrine eruption, wave by wave — each wave has its own voice
        int target = t < WAVE_START ? 0 : Math.min(9, (int) ((t - WAVE_START) / waveGap) + 1);
        while (currentWave < target) {
            currentWave++;
            buildWave(currentWave);
            Location burst = Builder.toWorld(this, 0, 2 + currentWave, 0);
            world.spawnParticle(Particle.BLOCK, burst, 24, 2.0, 0.8, 2.0, 0.08, DEBRIS);
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, burst, 18, 1.8, 0.8, 1.8, 0, EDGE_DUST);
            world.spawnParticle(Particle.LARGE_SMOKE, burst, 8, 1.2, 0.5, 1.2, 0.02);
            switch (currentWave) {
                case 1 -> {
                    world.playSound(shrineCenter, Sound.BLOCK_DEEPSLATE_BREAK, 2.2f, 0.42f);
                    world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.2f, 0.52f);
                    world.playSound(shrineCenter, Sound.BLOCK_NETHERITE_BLOCK_PLACE, 1.2f, 0.6f);
                }
                case 2 -> {
                    world.playSound(shrineCenter, Sound.ENTITY_WARDEN_DIG, 3.2f, 0.58f);
                    world.playSound(shrineCenter, Sound.BLOCK_BONE_BLOCK_BREAK, 2.2f, 0.48f);
                }
                case 3 -> {
                    world.playSound(shrineCenter, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.2f, 0.52f);
                    world.playSound(shrineCenter, Sound.BLOCK_ANVIL_BREAK, 1.5f, 0.68f);
                    world.playSound(shrineCenter, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.7f);
                }
                case 5 -> {
                    world.playSound(shrineCenter, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.8f, 0.48f);
                    world.playSound(shrineCenter, Sound.BLOCK_BEACON_ACTIVATE, 2.2f, 0.48f);
                    world.playSound(shrineCenter, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 0.6f);
                }
                case 7 -> {
                    world.playSound(shrineCenter, Sound.ENTITY_GHAST_SHOOT, 2.8f, 0.78f);
                    world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.7f, 0.68f);
                    world.playSound(shrineCenter, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.8f);
                }
                case 9 -> {
                    world.playSound(shrineCenter, Sound.ENTITY_WARDEN_EMERGE, 3.2f, 0.48f);
                    world.playSound(shrineCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.8f, 0.62f);
                    world.playSound(shrineCenter, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.2f);
                    world.spawnParticle(Particle.FLASH, shrineCenter.clone().add(0, 6, 0), 1, 0, 0, 0, 0, Color.fromRGB(140, 0, 0));
                }
                default -> {
                    world.playSound(shrineCenter, Sound.BLOCK_DEEPSLATE_BREAK, 2.1f, 0.48f);
                    world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.1f, 0.58f);
                }
            }
            if (groundThump) thump(30.0);
            if (rumbleEnabled) tremor(0.75 + currentWave * 0.12, 380L, 1.5, false);
        }

        if (due(2, t, 1.0)) {
            world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.5f, 0.7f);
        }
        if (due(14, t, 0.5)) {
            // Terrain tearing outward: debris + crackle
            Location ring = shrineCenter.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 20.0,
                    1.0,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 20.0);
            world.playSound(ring, Sound.BLOCK_DEEPSLATE_BREAK, 1.2f, 0.65f);
            world.spawnParticle(Particle.LARGE_SMOKE, ring, 3, 1.0, 0.5, 1.0, 0.01);
        }
    }

    private void buildWave(int wave) {
        switch (wave) {
            case 1 -> Builder.buildWave1(this);
            case 2 -> Builder.buildWave2(this);
            case 3 -> Builder.buildWave3(this);
            case 4 -> Builder.buildWave4(this);
            case 5 -> Builder.buildWave5(this);
            case 6 -> Builder.buildWave6(this);
            case 7 -> Builder.buildWave7(this);
            case 8 -> Builder.buildWave8(this);
            case 9 -> Builder.buildWave9(this);
            default -> { }
        }
    }

    private void thump(double range) {
        World world = shrineCenter.getWorld();
        if (world == null) return;
        double r2 = range * range;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(shrineCenter) > r2) continue;
            if (!p.isOnGround()) continue;
            p.setVelocity(p.getVelocity().add(new Vector(0, 0.1, 0)));
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // RUMBLE: ground tremors + camera shake for everyone in range
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * One rumble pulse: layered low-end sounds at the shrine, crust dust kicked up
     * around grounded players, a tiny vertical hop, and a decaying oscillating camera
     * shake. Never launches anyone — the hop is ~0.06 blocks/tick and only while on
     * the ground. {@code intensity} is multiplied by the configured
     * {@code rumble-intensity}; {@code withSounds} is false during the awakening,
     * where every wave already has its own voice.
     */
    private void tremor(double intensity, long durationMs, double amplitude, boolean withSounds) {
        if (!rumbleEnabled) return;
        World world = shrineCenter.getWorld();
        if (world == null) return;

        double scale = rumbleIntensity * Math.max(0.0, intensity);
        if (scale <= 0.0) return;

        if (withSounds) {
            world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 3.2f, 0.38f);
            world.playSound(shrineCenter, Sound.BLOCK_DEEPSLATE_BREAK, 1.6f, 0.30f);
            world.playSound(shrineCenter, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.50f);
            world.playSound(shrineCenter, Sound.ENTITY_WARDEN_EMERGE, 0.9f, 0.45f);
        }

        double range = radius + 12.0;
        double r2 = range * range;
        Location c = getCenter();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(c) > r2) continue;
            Location feet = p.getLocation();

            // Decaying sinusoidal wobble around the pre-shake rotation (no drift, no snap)
            registerShake(p, new Shake(feet.getYaw(), feet.getPitch(), amplitude * scale, durationMs));

            if (p.isOnGround() && p.getVehicle() == null) {
                p.setVelocity(p.getVelocity().add(new Vector(0, 0.055 * scale, 0)));
            }
            // Crust dust and ash kicked up off the ground beneath them
            world.spawnParticle(Particle.BLOCK, feet.clone().add(0, 0.08, 0), 7,
                    0.9, 0.06, 0.9, 0.03, DEBRIS);
            world.spawnParticle(Particle.ASH, feet.clone().add(0, 0.4, 0), 5,
                    0.9, 0.35, 0.9, 0);
            world.spawnParticle(Particle.POOF, feet.clone().add(0, 0.15, 0), 4,
                    0.8, 0.08, 0.8, 0.01);
        }
    }

    /** Registers a shake without dropping the base of one that is still active. */
    private void registerShake(Player player, Shake incoming) {
        shakes.compute(player.getUniqueId(), (ignored, active) -> Shake.layer(active, incoming));
    }

    /**
     * Registers a session-owned rotation jitter for one player. Their current
     * facing becomes the shake's base, so the per-tick driver restores it
     * exactly at expiry — and {@link #close()} restores it if the domain ends
     * mid-shake.
     */
    void jitter(Player player, double amplitude, long durationMs) {
        registerShake(player, new Shake(player.getLocation().getYaw(),
                player.getLocation().getPitch(), amplitude, System.currentTimeMillis(), durationMs));
    }

    /** Per-tick driver for {@link Shake}s. Cheap no-op while nothing is shaking. */
    private void applyShakes() {
        if (shakes.isEmpty()) return;
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Shake>> it = shakes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Shake> entry = it.next();
            Shake s = entry.getValue();
            long elapsed = now - s.startMs;

            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline() || p.isDead()) {
                it.remove();
                continue;
            }
            if (elapsed >= s.durationMs) {
                // Land exactly back on their pre-shake rotation
                p.setRotation(s.baseYaw, s.basePitch);
                it.remove();
                continue;
            }
            Rotation rotation = s.rotationAt(now);
            p.setRotation(rotation.yaw(), rotation.pitch());
        }
    }

    /** Called from close(): put everyone back the way the shakes found them. */
    private void restoreShakes() {
        for (Map.Entry<UUID, Shake> entry : shakes.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null && p.isOnline()) p.setRotation(entry.getValue().baseYaw, entry.getValue().basePitch);
        }
        shakes.clear();
    }

    private void refreshSkyPlayers() {
        World world = shrineCenter.getWorld();
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            boolean near = p.getLocation().distanceSquared(shrineCenter) < 10000;
            if (near) {
                if (skyPlayers.add(p.getUniqueId()) && phase >= 2) OpenBarrier.applySky(p);
            } else if (skyPlayers.remove(p.getUniqueId())) {
                OpenBarrier.resetSky(p);
            }
        }
    }

    /** Glowing blood-dust ring riding the leading edge of the tide. */
    private void spawnTideRing(World world, double front) {
        int pts = (int) Math.min(140, Math.max(24, front * 2.8));
        for (int i = 0; i < pts; i++) {
            double a = (Math.PI * 2 * i) / pts;
            double x = shrineCenter.getX() + Math.cos(a) * front;
            double z = shrineCenter.getZ() + Math.sin(a) * front;
            double y = floor.groundY(x, z, shrineCenter.getY() - 1.0) + 0.22;
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.1, 0.05, 0.1, 0, BLOOD_DUST);
            if (i % 6 == 0) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, x, y + 0.25, z, 1, 0.02, 0.08, 0.02, 0, EDGE_DUST);
            }
            if (i % 9 == 0) {
                world.spawnParticle(Particle.LARGE_SMOKE, x, y + 0.05, z, 1, 0.05, 0.02, 0.05, 0.0);
            }
        }
    }

    /** Ambient cursed haze drifting above the domain interior. */
    private void spawnCursedHaze(World world, double t) {
        Location c = getCenter();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = (int) Math.min(14, Math.max(6, Math.round(getRadius() / 12.0)));
        if (getRadius() > 128.0) count = 8;
        double r = Math.min(radius * 0.95, 28.0);
        double cull2 = visualRangeSq();
        for (int i = 0; i < count; i++) {
            double ang = rnd.nextDouble() * Math.PI * 2.0;
            double dist = Math.sqrt(rnd.nextDouble()) * r;
            double x = c.getX() + Math.cos(ang) * dist;
            double z = c.getZ() + Math.sin(ang) * dist;
            boolean any = false;
            for (Player p : world.getPlayers()) {
                double dx = p.getLocation().getX() - x, dz = p.getLocation().getZ() - z;
                if (dx * dx + dz * dz <= cull2) {
                    any = true;
                    break;
                }
            }
            if (!any) continue;
            double y = floor.groundY(x, z, c.getY()) + 0.3 + rnd.nextDouble() * 1.8;
            world.spawnParticle(Particle.DUST, x, y, z, 1, 0.08, 0.06, 0.08, 0, BLOOD_DUST);
            if (rnd.nextInt(4) == 0) {
                world.spawnParticle(Particle.ASH, x, y + 0.6, z, 1, 0.2, 0.2, 0.2, 0);
            }
        }
        // Occasional cursed embers near the shrine crown
        if (rnd.nextInt(3) == 0) {
            Location crown = Builder.toWorld(this, 0, 14 + Math.sin(t) * 0.5, 0);
            world.spawnParticle(Particle.CRIMSON_SPORE, crown, 8, 2.5, 1.2, 2.5, 0.02);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // DOMAIN
    // ═════════════════════════════════════════════════════════════════════════

    private void tickDomain(double t) {
        World world = shrineCenter.getWorld();
        if (world == null) return;
        domainElapsed = t;

        if (!domainInit) {
            domainInit = true;
            // Safety: finish anything the awakening could not (lag spikes)
            while (currentWave < 9) {
                currentWave++;
                buildWave(currentWave);
            }
            floor.expand(1.0);
            for (UUID id : skyPlayers) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline() && shrineCenter.getWorld() != null && p.getWorld() == shrineCenter.getWorld()) OpenBarrier.applySky(p);
            }
            // The domain SLAMS shut — one heavy shockwave before the sure-hit begins
            if (rumbleEnabled) tremor(2.1, 900L, 3.1, true);
        }
        if (!buffsApplied) applyCasterBuffs();

        // ── Sure-hit ──
        if (due(0, t, cleaveInterval)) Cleave.cleave(this, caster);
        if (t >= 0.5 && due(1, t - 0.5, 1.0)) Dismantle.pulse(this, caster);
        Dismantle.trackProjectiles(this, caster);
        Dismantle.tickTrackedProjectiles(this, caster);
        if (t >= 0.8 && due(3, t - 0.8, 2.6)) Dismantle.volley(this, caster);
        // Sparse RAVAGE micro-cuts: distant, horizontal tears between pulses
        if (due(14, t, 0.45)) Cleave.microCut(this, caster);
        // Telegraph whistle just before each main ravage
        if (due(15, t + cleaveInterval * 0.75, cleaveInterval)) {
            world.playSound(getCenter(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.45f);
            world.playSound(getCenter(), Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.9f);
        }

        // ── Living shrine ──
        if (due(4, t, 0.1)) {
            Builder.animateCore(this, t);
            Builder.swaySkulls(this, t);
        }
        if (due(5, t, 0.2)) Builder.animateJaws(this, t);

        // ── Floor ──
        if (due(6, t, 1.6)) ripples.add(t);
        if (due(7, t, 0.1)) drawRipples(t);

        // ── Atmosphere ──
        if (due(8, t, 0.16)) spawnEdgeWall(t);
        if (due(9, t, 0.32)) spawnSlashStorm();
        if (due(10, t, 0.35)) spawnMistAndAsh(world);
        if (due(11, t, 1.8)) world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.2f, 0.82f);
        if (due(12, t, 0.5)) refreshSkyPlayers();
        if (due(13, t, 0.25)) sendDomainHud(t);
        if (due(19, t, 0.22)) spawnCursedHaze(world, t);
        
        // Global Domain Aura: Crimson Spores drifting through the domain volume
        if (due(21, t, 0.28)) {
            Location c = getCenter();
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            double r = Math.min(radius * 0.95, 32.0);
            for (int k = 0; k < 6; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2.0;
                double dist = Math.sqrt(rnd.nextDouble()) * r;
                double px = c.getX() + Math.cos(ang) * dist;
                double pz = c.getZ() + Math.sin(ang) * dist;
                double py = floor.groundY(px, pz, c.getY()) + 0.5 + rnd.nextDouble() * 4.0;
                world.spawnParticle(Particle.CRIMSON_SPORE, px, py, pz, 1, 0.1, 0.05, 0.1, 0.01);
            }
        }
        // Distant malevolent choirs every ~7s so the arena never feels silent
        if (due(16, t, 7.0)) {
            world.playSound(shrineCenter, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.7f, 0.72f);
            world.playSound(shrineCenter, Sound.BLOCK_BELL_RESONATE, 1.9f, 0.48f);
            world.playSound(shrineCenter, Sound.AMBIENT_CRIMSON_FOREST_MOOD, 0.8f, 0.9f);
        }
        // Bone snaps and wet chomps from the mouths between pulses
        if (due(17, t, 0.4)) {
            Location mouth = Builder.mouthLocation(this, ThreadLocalRandom.current().nextInt(4));
            world.playSound(mouth, Sound.BLOCK_BONE_BLOCK_BREAK, 0.95f, 0.85f + ThreadLocalRandom.current().nextFloat() * 0.5f);
            world.playSound(mouth, Sound.ENTITY_GENERIC_EAT, 0.35f, 0.55f);
        }
        // Periodic deep rumble: the arena itself feels like it is breathing
        if (rumbleEnabled && due(20, t, 2.4)) tremor(1.15, 620L, 2.2, true);
    }

    private void applyCasterBuffs() {
        buffsApplied = true;
        if (!casterBuffsEnabled || !caster.isOnline()) return;

        int durationTicks = (int) Math.round((domainSeconds + collapseSeconds) * 20);

        // Resistance II only. Grant it only when it would actually improve on
        // what the caster already has, and remember that we did — so close()
        // strips only the buff this domain granted, never one the caster
        // walked in with.
        PotionEffect existing = caster.getPotionEffect(PotionEffectType.RESISTANCE);
        boolean grant = ShrineSessionDecision.shouldGrantResistance(
                existing == null ? -1 : existing.getAmplifier(),
                existing == null ? 0 : existing.getDuration(),
                durationTicks);
        if (grant) {
            caster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, durationTicks, 1, false, false, true));
            grantedResistance = true;
        }
    }

    /** Rolling rings of blood-dust that pulse outward across the settled floor. */
    private void drawRipples(double t) {
        double maxR = getRadius() + 10.0;
        Iterator<Double> it = ripples.iterator();
        List<Double> alive = new ArrayList<>();
        while (it.hasNext()) {
            double born = it.next();
            double r = (t - born) * 14.0;
            if (r > maxR) continue;
            floor.ripple(r);
            alive.add(born);
        }
        ripples.clear();
        ripples.addAll(alive);
    }

    /** Radius^2 for expensive ambient spawns; outside it we skip the work. */
    private double visualRangeSq() {
        // The 200m shrine cannot push hundreds of particles to every player.
        // Cap the visual falloff at ~96m and tighten it further on very large radii.
        double range = getRadius() > 128.0 ? 80.0 : Math.min(getRadius(), 112.0);
        return range * range;
    }

    /** The pale energy edge: points scale with radius and only replicate within visual range. */
    private void spawnEdgeWall(double t) {
        World world = shrineCenter.getWorld();
        if (world == null) return;

        Location center = getCenter();
        double r = getRadius();
        int points = (int) Math.min(120, Math.max(36, r * 0.85));
        double offset = t * 0.12;
        double r2 = visualRangeSq();
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(center) > r2) continue;
            for (int i = 0; i < points; i++) {
                double angle = (Math.PI * 2 * i) / points + offset;
                double px = center.getX() + Math.cos(angle) * r;
                double pz = center.getZ() + Math.sin(angle) * r;
                double gy = floor.groundY(px, pz, center.getY());
                for (int dy = 0; dy < 3; dy++) {
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, px, gy + 0.3 + dy, pz, 1, 0, 0, 0, 0, EDGE_DUST);
                }
            }
            break; // field is uniform enough; one nearby player is enough to anchor the edge
        }
    }

    /** Dismantle ambience: sparse, long horizontal cuts across the domain. */
    private void spawnSlashStorm() {
        World world = shrineCenter.getWorld();
        if (world == null) return;

        List<Player> targets = new ArrayList<>();
        double r2 = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead()) continue;
            if (p.equals(caster)) continue;
            if (p.getLocation().distanceSquared(getCenter()) <= r2) targets.add(p);
        }
        if (targets.isEmpty()) targets.add(caster); // solo testing: still see the storm

        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int count = 1 + rnd.nextInt(2); // 1–2 sparse cuts per tick-cycle
        for (int i = 0; i < count; i++) {
            Player target = targets.get(rnd.nextInt(targets.size()));
            Location around = target.getLocation().add(
                    (rnd.nextDouble() - 0.5) * 10.0,
                    0.8 + rnd.nextDouble() * 1.4,
                    (rnd.nextDouble() - 0.5) * 10.0);
            Slashes.flurry(slashDisplays, around, 1, 10.0 + rnd.nextDouble() * 4.0);
        }
    }

    /** Black smoke pouring out of the four mouths, ash falling off the roof and around players. */
    private void spawnMistAndAsh(World world) {
        for (int m = 0; m < 4; m++) {
            Location l = Builder.mouthLocation(this, m);
            world.spawnParticle(Particle.LARGE_SMOKE, l, 3, 0.6, 0.5, 0.6, 0.01);
        }
        Location roof = Builder.toWorld(this, 0, 10, 0);
        world.spawnParticle(Particle.ASH, roof, 14, 9, 4, 7, 0);

        double r2 = radius * radius;
        for (Player p : world.getPlayers()) {
            if (p.getLocation().distanceSquared(getCenter()) > r2) continue;
            world.spawnParticle(Particle.ASH, p.getLocation().add(0, 2, 0), 12, 6, 3, 6, 0);
        }
    }

    private void sendDomainHud(double t) {
        double remaining = Math.max(0.0, domainSeconds - t);
        int totalBars = 30;
        int filled = Math.min(totalBars, Math.max(0, (int) Math.round((t / domainSeconds) * totalBars)));
        double pulse = 0.5 + 0.5 * Math.sin(t * 4.0);
        String barColor = pulse > 0.5 ? "<dark_red>" : "<red>";
        String icon = pulse > 0.7 ? "<red>⛩" : "<dark_red>⛩";

        caster.sendActionBar(MiniMessage.miniMessage().deserialize("<dark_gray>[ " + barColor + "▰".repeat(filled)
                + "<gray>" + "▱".repeat(totalBars - filled)
                + " <dark_gray>] " + icon + " <red>Malevolent Shrine <white>" + (int) Math.ceil(remaining) + "s"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // COLLAPSE: tremor, top-down dismantling, receding tide, sky roll-back
    // ═════════════════════════════════════════════════════════════════════════

    private void tickCollapse(double t) {
        World world = shrineCenter.getWorld();
        if (world == null) return;

        if (!collapseStarted) {
            collapseStarted = true;
            world.playSound(shrineCenter, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
            world.playSound(shrineCenter, Sound.BLOCK_BEACON_DEACTIVATE, 2.0f, 0.6f);

            caster.showTitle(Title.title(
                    Component.empty(),
                    MiniMessage.miniMessage().deserialize("<gray><italic>The shrine returns to the earth...."),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(2400), Duration.ofMillis(800))));

            // Jaws clamp shut for good
            biteUntil = Double.MAX_VALUE;
            Builder.animateJaws(this, domainElapsed);
            if (rumbleEnabled) tremor(1.9, 900L, 2.8, false);
        }

        // Phase 1 (0 - 0.9s): the whole structure trembles
        if (t < 0.9 && due(0, t, 0.1)) tremble();
        if (t < 0.9 && rumbleEnabled && due(21, t, 0.4)) tremor(1.4, 420L, 2.3, false);

        // Phase 2: dismantle top-down, wave 9 first, one wave every 0.38s
        int order = t < 0.9 ? 0 : Math.min(9, (int) ((t - 0.9) / 0.38) + 1);
        while (collapsedWaves < order) {
            collapsedWaves++;
            collapseWave(10 - collapsedWaves);
        }

        // The tide recedes as a burning rim shrinking toward the shrine
        double p = MathUtil.clamp((t - 1.0) / 3.6, 0.0, 1.0);
        double eased = MathUtil.smoothstep(p);
        floor.recede(eased);
        if (p > 0.0 && p < 1.0) {
            floor.recedeRim(floor.frontRadius(1.0 - eased));
        }

        // Sky rolls back to the real time
        double skyP = MathUtil.clamp((t - 2.0) / 3.0, 0.0, 1.0);
        for (UUID id : skyPlayers) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null && pl.isOnline()) OpenBarrier.fadeOut(pl, skyP);
        }

        if (due(1, t, 0.25)) {
            world.spawnParticle(Particle.ASH, Builder.toWorld(this, 0, 8, 0), 25, 10, 6, 8, 0);
        }
        if (due(2, t, 1.0)) {
            world.playSound(shrineCenter, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 0.5f);
        }
    }

    /** Small random shake of a quarter of all pieces, every 2 ticks. */
    private void tremble() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (Builder.Piece p : pieces) {
            if (r.nextInt(4) != 0) continue;
            BlockDisplay d = p.display();
            if (!d.isValid()) continue;
            d.setTeleportDuration(2);
            d.teleport(p.home().clone().add(
                    (r.nextDouble() - 0.5) * 0.24,
                    (r.nextDouble() - 0.5) * 0.12,
                    (r.nextDouble() - 0.5) * 0.24));
        }
    }

    /** Every piece of one build wave tilts, tumbles and sinks into the earth with a random delay. */
    private void collapseWave(int wave) {
        World world = shrineCenter.getWorld();
        if (world == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ShenanigansCore plugin = ShenanigansCore.getInstance();

        boolean sounded = false;
        for (Builder.Piece p : pieces) {
            if (p.wave() != wave) continue;
            BlockDisplay d = p.display();
            if (!sounded) {
                sounded = true;
                world.playSound(d.getLocation(), Sound.BLOCK_DEEPSLATE_BREAK, 2.0f, 0.5f);
            }
            int delay = r.nextInt(7);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!isActive() || !d.isValid()) return;
                Location now = d.getLocation();
                world.spawnParticle(Particle.ASH, now, 3, 0.4, 0.4, 0.4, 0);
                if (r.nextInt(3) == 0) {
                    world.spawnParticle(Particle.BLOCK, now, 4, 0.4, 0.3, 0.4, 0.05, DEBRIS);
                }

                TransformUtils.applyTransform(d, p.sx(), p.sy(), p.sz(), p.yaw(),
                        p.leanX() + r.nextFloat(-28f, 28f), p.leanZ() + r.nextFloat(-28f, 28f), 16);
                d.setTeleportDuration(22);
                d.teleport(now.clone().add(
                        (r.nextDouble() - 0.5) * 3.0,
                        -(10.0 + r.nextDouble() * 4.0),
                        (r.nextDouble() - 0.5) * 3.0));
            }, delay);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (d.isValid()) d.remove();
            }, delay + 28L);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ═════════════════════════════════════════════════════════════════════════

    @Override
    public void close() {
        if (!isActive()) return;

        if (task != null && !task.isCancelled()) task.cancel();

        cleanupVerticalHandling();
        DomainExitController.returnCaptives(this);

        // Take back only the Resistance THIS domain granted — never a Resistance
        // the caster walked in with.
        if (ShrineSessionDecision.shouldRemoveResistance(grantedResistance)) {
            caster.removePotionEffect(PotionEffectType.RESISTANCE);
        }

        // Nobody leaves the arena still shaking
        restoreShakes();

        // Give everyone their sky (and weather) back
        for (UUID uuid : skyPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) OpenBarrier.resetSky(p);
        }
        skyPlayers.clear();

        // Slashes self-remove, but delayed RAVAGE stages can still be in flight
        // when a forced close ends the domain: drop every owned display.
        slashDisplays.dispose();

        // Remove every display entity
        for (Entity e : spawnedDisplays) {
            if (e != null && e.isValid()) e.remove();
        }
        spawnedDisplays.clear();
        pieces.clear();
        jaws.clear();
        hanging.clear();
        wisps.clear();
        ravageCounts.clear();
        trackedProjectileCuts.clear();
        shrineCore = null;
        shrineCrest = null;

        // Hand the real terrain back to every viewer
        floor.restoreAll();
        changedBlocks.clear();
        restorePhysicalBlocks();
        // Safety: ensure no claimed physical blocks linger in registry.
        DomainRegistry.get().release(getUniqueId());

        deactivate();
        ShenanigansCore.getInstance().getSessionManager().removeSession(this);
    }
}
