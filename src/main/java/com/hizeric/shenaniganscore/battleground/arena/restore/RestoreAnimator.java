package com.hizeric.shenaniganscore.battleground.arena.restore;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.snapshot.ArenaSnapshot.ChunkPos;
import com.hizeric.shenaniganscore.core.util.BlockKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Restore animation layer: while the arena rebuilds, nearby players see a sweeping shimmer, hear a
 * rising chime, and get a live progress bar.
 *
 * <ul>
 *   <li><b>Sparkles</b> rise from a sample of the blocks just restored in each chunk (blue enchant
 *       glyphs for tier-1 deployment blocks, white end-rod sparks for the rest).</li>
 *   <li><b>Chunk outline</b>: a thin shimmer traces the restored chunk's border so the repair reads
 *       as a wave moving across the map.</li>
 *   <li><b>Chime</b>: pitch climbs with overall progress, so the restore "plays a scale".</li>
 *   <li><b>Action bar</b>: {@code Restoring arena ████████░░░░ 63%}, then a toast sound when done.</li>
 * </ul>
 *
 * <p>Cost control: nothing runs unless a player is within view distance of the chunk; particles
 * are capped per chunk and per server tick; sounds are rate limited. Tune it with {@link Settings}.</p>
 */
public final class RestoreAnimator implements RestoreEffects {

    /**
     * @param enabled            master switch
     * @param particlesPerChunk  sparkles per restored chunk (0 disables sparkles)
     * @param outline            trace the chunk border with a shimmer
     * @param maxParticlesPerTick hard cap on particle packets requested in one tick
     * @param sounds             chime per chunk, deployment-ready and completion sounds
     * @param actionBar          show the progress bar
     * @param viewDistance       blocks; chunks farther than this from every viewer are skipped
     */
    public record Settings(boolean enabled, int particlesPerChunk, boolean outline,
                           int maxParticlesPerTick, boolean sounds, boolean actionBar,
                           boolean bossBar, int viewDistance) {

        public static Settings defaults() {
            // Action bar is disabled by default: it clashes with the ability HUD's action bar.
            // Progress is shown on a boss bar instead.
            return new Settings(true, 18, true, 160, true, false, true, 64);
        }

        public static Settings off() {
            return new Settings(false, 0, false, 0, false, false, false, 0);
        }
    }

    private static final int BAR_WIDTH = 20;
    private static final int CHIME_COOLDOWN_TICKS = 3;
    private static final int OUTLINE_STEP = 4;

    private final Settings settings;

    private BossBar bossBar;

    private int minX, maxX, minZ, maxZ;
    private boolean hasBounds;
    private int percent;
    private int budgetTick = Integer.MIN_VALUE;
    private int budgetLeft;
    private int lastChimeTick = Integer.MIN_VALUE;

    public RestoreAnimator(Settings settings) {
        this.settings = settings == null ? Settings.defaults() : settings;
    }

    @Override
    public void onStart(World world, ArenaRegion region) {
        percent = 0;
        lastChimeTick = Integer.MIN_VALUE;
        hasBounds = region != null;
        if (hasBounds) {
            minX = Math.min(region.min().getBlockX(), region.max().getBlockX());
            maxX = Math.max(region.min().getBlockX(), region.max().getBlockX());
            minZ = Math.min(region.min().getBlockZ(), region.max().getBlockZ());
            maxZ = Math.max(region.min().getBlockZ(), region.max().getBlockZ());
        }
        if (settings.enabled() && settings.bossBar() && world != null) {
            if (bossBar != null) bossBar.removeAll();
            bossBar = Bukkit.createBossBar("Restoring arena 0%",
                    BarColor.BLUE, BarStyle.SEGMENTED_20);
            bossBar.setProgress(0.0);
            for (Player player : arenaPlayers(world)) bossBar.addPlayer(player);
        }
        if (settings.enabled() && settings.actionBar() && world != null) {
            sendBar(world, 0);
        }
    }

    @Override
    public void onChunkRestored(World world, ChunkPos chunk, List<Long> written, boolean tierOne) {
        if (!settings.enabled() || world == null || written == null || written.isEmpty()) return;

        double centerX = chunk.x() * 16 + 8.0;
        double centerZ = chunk.z() * 16 + 8.0;
        List<Player> viewers = viewersNear(world, centerX, centerZ, settings.viewDistance());
        if (viewers.isEmpty()) return; // nobody is looking: spend nothing

        int tick = Bukkit.getCurrentTick();
        if (tick != budgetTick) {
            budgetTick = tick;
            budgetLeft = settings.maxParticlesPerTick();
        }

        Particle spark = tierOne ? Particle.ENCHANT : Particle.END_ROD;
        double ySum = 0;
        int sparks = Math.min(Math.min(settings.particlesPerChunk(), written.size()), budgetLeft);
        int stride = Math.max(1, written.size() / Math.max(1, sparks));
        int emitted = 0;
        for (int i = 0; i < written.size(); i++) {
            long packed = written.get(i);
            ySum += BlockKey.unpackY(packed);
            if (emitted < sparks && i % stride == 0) {
                world.spawnParticle(spark,
                        BlockKey.unpackX(packed) + 0.5, BlockKey.unpackY(packed) + 0.8,
                        BlockKey.unpackZ(packed) + 0.5,
                        1, 0.2, 0.3, 0.2, 0.02);
                emitted++;
            }
        }
        budgetLeft -= emitted;

        if (settings.outline() && budgetLeft >= outlineCost()) {
            budgetLeft -= drawOutline(world, chunk, ySum / written.size() + 1.0);
        }

        if (settings.sounds() && tick - lastChimeTick >= CHIME_COOLDOWN_TICKS) {
            lastChimeTick = tick;
            float pitch = 0.7f + 1.3f * (percent / 100f);
            world.playSound(new Location(world, centerX, ySum / written.size(), centerZ),
                    Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 0.4f, pitch);
        }
    }

    @Override
    public void onTierOneComplete(World world) {
        if (!settings.enabled() || world == null) return;
        if (settings.sounds()) {
            for (Player player : arenaPlayers(world)) {
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f);
            }
        }
        if (settings.bossBar() && bossBar != null) {
            bossBar.setTitle("Deployment ready");
            bossBar.setColor(BarColor.GREEN);
        }
        if (settings.actionBar()) {
            Component ready = Component.text("Deployment ready", NamedTextColor.GREEN);
            for (Player player : arenaPlayers(world)) player.sendActionBar(ready);
        }
    }

    @Override
    public void onProgress(World world, int percent) {
        this.percent = Math.max(0, Math.min(100, percent));
        if (settings.enabled() && settings.bossBar() && bossBar != null) {
            bossBar.setProgress(this.percent / 100.0);
            bossBar.setTitle("Restoring arena " + this.percent + "%");
            if (world != null) {
                for (Player player : arenaPlayers(world)) bossBar.addPlayer(player);
            }
        }
        if (settings.enabled() && settings.actionBar() && world != null) {
            sendBar(world, this.percent);
        }
    }

    @Override
    public void onFinished(World world, boolean failed) {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar = null;
        }
        if (!settings.enabled() || world == null || failed) return;
        Component done = Component.text("Arena restored", NamedTextColor.GREEN);
        for (Player player : arenaPlayers(world)) {
            if (settings.actionBar()) player.sendActionBar(done);
            if (settings.sounds()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private void sendBar(World world, int percent) {
        int filled = Math.round(BAR_WIDTH * percent / 100f);
        Component bar = Component.text("Restoring arena ", NamedTextColor.AQUA)
                .append(Component.text("█".repeat(filled), NamedTextColor.AQUA))
                .append(Component.text("█".repeat(BAR_WIDTH - filled), NamedTextColor.DARK_GRAY))
                .append(Component.text(" " + percent + "%", NamedTextColor.WHITE));
        for (Player player : arenaPlayers(world)) player.sendActionBar(bar);
    }

    private static int outlineCost() {
        return (16 / OUTLINE_STEP) * 4;
    }

    /** Traces the four edges of the chunk at the given height. Returns packets requested. */
    private int drawOutline(World world, ChunkPos chunk, double y) {
        double x0 = chunk.x() * 16.0;
        double z0 = chunk.z() * 16.0;
        int count = 0;
        for (int step = 0; step < 16; step += OUTLINE_STEP) {
            world.spawnParticle(Particle.END_ROD, x0 + step + 0.5, y, z0, 1, 0, 0.1, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, x0 + step + 0.5, y, z0 + 16, 1, 0, 0.1, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, x0, y, z0 + step + 0.5, 1, 0, 0.1, 0, 0.0);
            world.spawnParticle(Particle.END_ROD, x0 + 16, y, z0 + step + 0.5, 1, 0, 0.1, 0, 0.0);
            count += 4;
        }
        return count;
    }

    private List<Player> viewersNear(World world, double x, double z, int distance) {
        List<Player> near = new ArrayList<>();
        double limit = (double) distance * distance;
        for (Player player : world.getPlayers()) {
            Location at = player.getLocation();
            double dx = at.getX() - x;
            double dz = at.getZ() - z;
            if (dx * dx + dz * dz <= limit) near.add(player);
        }
        return near;
    }

    /** Players currently inside (or within 16 blocks of) the arena footprint. */
    private List<Player> arenaPlayers(World world) {
        List<Player> inside = new ArrayList<>();
        for (Player player : world.getPlayers()) {
            if (!hasBounds) {
                inside.add(player);
                continue;
            }
            Location at = player.getLocation();
            if (at.getX() >= minX - 16 && at.getX() <= maxX + 16
                    && at.getZ() >= minZ - 16 && at.getZ() <= maxZ + 16) {
                inside.add(player);
            }
        }
        return inside;
    }
}
