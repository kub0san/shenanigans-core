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
 */
public final class ArenaRuntimeConfig {

    private static final ArenaRuntimeConfig EMPTY = new ArenaRuntimeConfig(
            1200, 15, true,
            true,
            true, true, true,
            30, 256, 4, true);

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

    private ArenaRuntimeConfig(int roundDurationSeconds, int roundWarningSeconds, boolean roundPauseWhenEmpty,
                               boolean protectionEnabled, boolean restoreRelight, boolean restoreVerify,
                               boolean restoreFallbackOnUnverified, int restoreMsPerTick,
                               int restoreBlocksPerTick, int restoreChunksPerTick,
                               boolean restoreDeferNearPlayer) {
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
                loaded = new ArenaRuntimeConfig(
                        reader.number(round.node("duration-seconds"), "arena.round.duration-seconds",
                                1200, ROUND_MIN, ROUND_MAX),
                        reader.number(round.node("warning-seconds"), "arena.round.warning-seconds",
                                15, WARNING_MIN, WARNING_MAX),
                        reader.flag(round.node("pause-when-empty"), "arena.round.pause-when-empty", true),
                        reader.flag(protection.node("enabled"), "arena.protection.enabled", true),
                        reader.flag(restore.node("relight"), "arena.restore.relight", true),
                        reader.flag(restore.node("verify"), "arena.restore.verify", true),
                        reader.flag(restore.node("fallback-on-unverified"),
                                "arena.restore.fallback-on-unverified", true),
                        reader.number(restore.node("ms-per-tick"), "arena.restore.ms-per-tick",
                                30, MS_MIN, MS_MAX),
                        reader.number(restore.node("blocks-per-tick"), "arena.restore.blocks-per-tick",
                                256, BLOCKS_MIN, BLOCKS_MAX),
                        reader.number(restore.node("chunks-per-tick"), "arena.restore.chunks-per-tick",
                                4, CHUNKS_MIN, CHUNKS_MAX),
                        reader.flag(restore.node("defer-near-player"), "arena.restore.defer-near-player", true));
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

        private void reject(String key, Object raw) {
            if (warned.add(key) && logger != null) {
                logger.warning("Arena runtime config: invalid " + key + " value '" + raw
                        + "'; using the shipped default");
            }
        }
    }
}
