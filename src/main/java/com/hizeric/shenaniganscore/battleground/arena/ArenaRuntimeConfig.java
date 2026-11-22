package com.hizeric.shenaniganscore.battleground.arena;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Live arena, round and restore tuning read from {@code battleground/config.yml}.
 *
 * <p>The shipped values are the source of truth for every key; a missing, invalid or out-of-range
 * value falls back to the shipped default with one warning per key. {@link #load(File, Logger)}
 * atomically replaces {@link #current()}, so a {@code /bg reload} is visible to every later read.</p>
 *
 * <p>Every default now lives in exactly one place ({@link #EMPTY}); the loader falls back to those
 * fields instead of repeating the numbers, so they cannot drift apart.</p>
 *
 * <p>New optional keys under {@code arena.restore.animation} (absent keys use the defaults silently,
 * so existing config files keep working without warnings):</p>
 * <pre>
 * arena:
 *   restore:
 *     animation:
 *       enabled: true
 *       particles-per-chunk: 18   # 0 turns sparkles and the chunk outline off
 *       sounds: true
 *       action-bar: false
 * </pre>
 */
public final class ArenaRuntimeConfig {

    private static final ArenaRuntimeConfig EMPTY = new ArenaRuntimeConfig(
            1200, 15, true,
            true,
            true, true, true,
            30, 256, 4, true,
            true, 18, true, false);

    private static volatile ArenaRuntimeConfig current = EMPTY;

    private static final int ROUND_MIN = 60;
    private static final int ROUND_MAX = 86_400;
    private static final int WARNING_MIN = 1;
    private static final int WARNING_MAX = 300;
    private static final int MS_MIN = 1;
    private static final int MS_MAX = 50;
    private static final int BLOCKS_MIN = 1;
    private static final int BLOCKS_MAX = 4096;
    private static final int CHUNKS_MIN = 1;
    private static final int CHUNKS_MAX = 64;
    private static final int PARTICLES_MIN = 0;
    private static final int PARTICLES_MAX = 64;

    private final int roundDurationSeconds;
    private final int roundWarningSeconds;
    private final boolean roundPauseWhenEmpty;
    private final boolean protectionEnabled;
    private final boolean restoreRelight;
    private final boolean restoreVerify;
    private final boolean restoreFallbackOnUnverified;
    private final int restoreMsPerTick;
    private final int restoreBlocksPerTick;
    private final int restoreChunksPerTick;
    private final boolean restoreDeferNearPlayer;
    private final boolean restoreAnimationEnabled;
    private final int restoreAnimationParticles;
    private final boolean restoreAnimationSounds;
    private final boolean restoreAnimationActionBar;

    private ArenaRuntimeConfig(int roundDurationSeconds, int roundWarningSeconds, boolean roundPauseWhenEmpty,
                               boolean protectionEnabled, boolean restoreRelight, boolean restoreVerify,
                               boolean restoreFallbackOnUnverified, int restoreMsPerTick,
                               int restoreBlocksPerTick, int restoreChunksPerTick,
                               boolean restoreDeferNearPlayer, boolean restoreAnimationEnabled,
                               int restoreAnimationParticles, boolean restoreAnimationSounds,
                               boolean restoreAnimationActionBar) {
        this.roundDurationSeconds = roundDurationSeconds;
        this.roundWarningSeconds = roundWarningSeconds;
        this.roundPauseWhenEmpty = roundPauseWhenEmpty;
        this.protectionEnabled = protectionEnabled;
        this.restoreRelight = restoreRelight;
        this.restoreVerify = restoreVerify;
        this.restoreFallbackOnUnverified = restoreFallbackOnUnverified;
        this.restoreMsPerTick = restoreMsPerTick;
        this.restoreBlocksPerTick = restoreBlocksPerTick;
        this.restoreChunksPerTick = restoreChunksPerTick;
        this.restoreDeferNearPlayer = restoreDeferNearPlayer;
        this.restoreAnimationEnabled = restoreAnimationEnabled;
        this.restoreAnimationParticles = restoreAnimationParticles;
        this.restoreAnimationSounds = restoreAnimationSounds;
        this.restoreAnimationActionBar = restoreAnimationActionBar;
    }

    /** The shipped policy: every value comes from the bundled {@code battleground/config.yml}. */
    public static ArenaRuntimeConfig defaults() {
        return EMPTY;
    }

    /** The holder installed by the most recent {@link #load}, never null. */
    public static ArenaRuntimeConfig current() {
        return current;
    }

    public int roundDurationSeconds() { return roundDurationSeconds; }
    public int roundWarningSeconds() { return roundWarningSeconds; }
    public boolean roundPauseWhenEmpty() { return roundPauseWhenEmpty; }
    public boolean protectionEnabled() { return protectionEnabled; }
    public boolean restoreRelight() { return restoreRelight; }
    public boolean restoreVerify() { return restoreVerify; }
    public boolean restoreFallbackOnUnverified() { return restoreFallbackOnUnverified; }
    public int restoreMsPerTick() { return restoreMsPerTick; }
    public int restoreBlocksPerTick() { return restoreBlocksPerTick; }
    public int restoreChunksPerTick() { return restoreChunksPerTick; }
    public boolean restoreDeferNearPlayer() { return restoreDeferNearPlayer; }
    public boolean restoreAnimationEnabled() { return restoreAnimationEnabled; }
    public int restoreAnimationParticles() { return restoreAnimationParticles; }
    public boolean restoreAnimationSounds() { return restoreAnimationSounds; }
    public boolean restoreAnimationActionBar() { return restoreAnimationActionBar; }

    /** Re-reads {@code file} and installs the result; bad input installs {@link #defaults()}. */
    public static ArenaRuntimeConfig load(File file, Logger logger) {
        ArenaRuntimeConfig loaded;
        if (file == null || !file.isFile()) {
            if (logger != null) {
                logger.warning("Arena runtime config: " + (file == null ? "no config file" : file.getName())
                        + " not found; using shipped defaults");
            }
            loaded = EMPTY;
        } else {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                CommentedConfigurationNode root = YamlConfigurationLoader.builder()
                        .source(() -> new BufferedReader(
                                new StringReader(new String(bytes, StandardCharsets.UTF_8))))
                        .nodeStyle(NodeStyle.BLOCK)
                        .build()
                        .load();
                Loader reader = new Loader(logger);
                ConfigurationNode round = root.node("arena", "round");
                ConfigurationNode protection = root.node("arena", "protection");
                ConfigurationNode restore = root.node("arena", "restore");
                ConfigurationNode animation = restore.node("animation");
                ArenaRuntimeConfig d = EMPTY;
                loaded = new ArenaRuntimeConfig(
                        reader.number(round.node("duration-seconds"), "arena.round.duration-seconds",
                                d.roundDurationSeconds, ROUND_MIN, ROUND_MAX),
                        reader.number(round.node("warning-seconds"), "arena.round.warning-seconds",
                                d.roundWarningSeconds, WARNING_MIN, WARNING_MAX),
                        reader.flag(round.node("pause-when-empty"), "arena.round.pause-when-empty",
                                d.roundPauseWhenEmpty),
                        reader.flag(protection.node("enabled"), "arena.protection.enabled",
                                d.protectionEnabled),
                        reader.flag(restore.node("relight"), "arena.restore.relight", d.restoreRelight),
                        reader.flag(restore.node("verify"), "arena.restore.verify", d.restoreVerify),
                        reader.flag(restore.node("fallback-on-unverified"),
                                "arena.restore.fallback-on-unverified", d.restoreFallbackOnUnverified),
                        reader.number(restore.node("ms-per-tick"), "arena.restore.ms-per-tick",
                                d.restoreMsPerTick, MS_MIN, MS_MAX),
                        reader.number(restore.node("blocks-per-tick"), "arena.restore.blocks-per-tick",
                                d.restoreBlocksPerTick, BLOCKS_MIN, BLOCKS_MAX),
                        reader.number(restore.node("chunks-per-tick"), "arena.restore.chunks-per-tick",
                                d.restoreChunksPerTick, CHUNKS_MIN, CHUNKS_MAX),
                        reader.flag(restore.node("defer-near-player"), "arena.restore.defer-near-player",
                                d.restoreDeferNearPlayer),
                        reader.optionalFlag(animation.node("enabled"), "arena.restore.animation.enabled",
                                d.restoreAnimationEnabled),
                        reader.optionalNumber(animation.node("particles-per-chunk"),
                                "arena.restore.animation.particles-per-chunk",
                                d.restoreAnimationParticles, PARTICLES_MIN, PARTICLES_MAX),
                        reader.optionalFlag(animation.node("sounds"), "arena.restore.animation.sounds",
                                d.restoreAnimationSounds),
                        reader.optionalFlag(animation.node("action-bar"), "arena.restore.animation.action-bar",
                                d.restoreAnimationActionBar));
            } catch (IOException | RuntimeException e) {
                if (logger != null) {
                    logger.log(Level.WARNING,
                            "Arena runtime config: could not read " + file.getName()
                                    + "; using shipped defaults", e);
                }
                loaded = EMPTY;
            }
        }
        current = loaded;
        return loaded;
    }

    /** Alias for {@link #load} used by the reload paths: swaps {@link #current()}. */
    public static ArenaRuntimeConfig reload(File file, Logger logger) {
        return load(file, logger);
    }

    /** Per-load strict parser: one warning per bad key, never a warning in a hot read path. */
    private static final class Loader {

        private final Logger logger;
        private final Set<String> warned = new HashSet<>();

        private Loader(Logger logger) {
            this.logger = logger;
        }

        private int number(ConfigurationNode node, String key, int fallback, int min, int max) {
            Object raw = node.raw();
            if (raw instanceof Number number) {
                long value = number.longValue();
                if (number.doubleValue() == value && value >= min && value <= max) {
                    return (int) value;
                }
            }
            reject(key, raw);
            return fallback;
        }

        private boolean flag(ConfigurationNode node, String key, boolean fallback) {
            Object raw = node.raw();
            if (raw instanceof Boolean value) {
                return value;
            }
            reject(key, raw);
            return fallback;
        }

        /** Like {@link #number} but a key that is simply absent is not a problem. */
        private int optionalNumber(ConfigurationNode node, String key, int fallback, int min, int max) {
            return node.raw() == null ? fallback : number(node, key, fallback, min, max);
        }

        private boolean optionalFlag(ConfigurationNode node, String key, boolean fallback) {
            return node.raw() == null ? fallback : flag(node, key, fallback);
        }

        private void reject(String key, Object raw) {
            if (warned.add(key) && logger != null) {
                logger.warning("Arena runtime config: invalid " + key + " value '" + raw
                        + "'; using the shipped default");
            }
        }
    }
}
