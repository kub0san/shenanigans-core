package com.hizeric.shenaniganscore.core.config;

import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generic, id-keyed tuning values for abilities. This class deliberately knows nothing about
 * JJK, materials, or which ids exist: the caller supplies the id and the shipped fallback.
 *
 * <p>{@link #load(File, Logger)} atomically replaces {@link #current()} so a {@code /bg reload}
 * is visible to every cast-time read. A missing or unparseable file installs {@link #defaults()}
 * instead of throwing.</p>
 */
public final class AbilityTuningConfig {

    /** Which top-level {@code abilities} map a value belongs to. */
    public enum Section {
        ITEMS("items"),
        TECHNIQUES("techniques");

        private final String key;

        Section(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }

    private static final AbilityTuningConfig EMPTY = new AbilityTuningConfig(null, null);
    private static volatile AbilityTuningConfig current = EMPTY;

    private final CommentedConfigurationNode root;
    private final Logger logger;
    private final Set<String> warned = ConcurrentHashMap.newKeySet();

    private AbilityTuningConfig(CommentedConfigurationNode root, Logger logger) {
        this.root = root;
        this.logger = logger;
    }

    /** An always-available holder that returns every caller-supplied fallback. */
    public static AbilityTuningConfig defaults() {
        return EMPTY;
    }

    /** The holder installed by the most recent {@link #load}, never null. */
    public static AbilityTuningConfig current() {
        return current;
    }

    /** Reads, installs, and returns the holder for {@code file}; bad input yields {@link #defaults()}. */
    public static AbilityTuningConfig load(File file, Logger logger) {
        AbilityTuningConfig loaded;
        if (file == null || !file.isFile()) {
            if (logger != null) {
                logger.warning("Ability tuning: " + (file == null ? "no config file" : file.getName())
                        + " not found; using shipped defaults");
            }
            loaded = EMPTY;
        } else {
            try {
                loaded = new AbilityTuningConfig(
                        ConfigFiles.loadFromBytes(Files.readAllBytes(file.toPath())), logger);
            } catch (IOException | RuntimeException e) {
                if (logger != null) {
                    logger.log(Level.WARNING,
                            "Ability tuning: could not read " + file.getName() + "; using shipped defaults", e);
                }
                loaded = EMPTY;
            }
        }
        current = loaded;
        return loaded;
    }

    /** Cooldown in seconds, limited to 1..3600; invalid values return {@code fallback}. */
    public int cooldown(Section section, String id, int fallback) {
        if (root == null || section == null) return fallback;
        ConfigurationNode node = node(section, id, "cooldown");
        Object raw = node.raw();
        if (!(raw instanceof Number)) {
            reject(id, "cooldown", raw);
            return fallback;
        }
        int value = node.getInt();
        if (value < 1 || value > 3600) {
            reject(id, "cooldown", raw);
            return fallback;
        }
        return value;
    }

    /** Damage value, limited to 0..200; invalid values return {@code fallback}. */
    public double damage(String id, double fallback) {
        if (root == null) return fallback;
        ConfigurationNode node = root.node("abilities", "techniques", normalize(id), "damage");
        Object raw = node.raw();
        if (!(raw instanceof Number)) {
            reject(id, "damage", raw);
            return fallback;
        }
        double value = node.getDouble();
        if (!Double.isFinite(value) || value < 0.0 || value > 200.0) {
            reject(id, "damage", raw);
            return fallback;
        }
        return value;
    }

    /** Armor bypass flag; a non-boolean value returns {@code fallback}. */
    public boolean armorPiercing(String id, boolean fallback) {
        if (root == null) return fallback;
        ConfigurationNode node = root.node("abilities", "techniques", normalize(id), "armor-piercing");
        Object raw = node.raw();
        if (raw instanceof Boolean value) return value;
        reject(id, "armor-piercing", raw);
        return fallback;
    }

    private ConfigurationNode node(Section section, String id, String field) {
        return root.node("abilities", section.key(), normalize(id), field);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** One warning per id/field per load, so a bad value cannot spam a cast hot path. */
    private void reject(String id, String field, Object raw) {
        String key = normalize(id) + "." + field;
        if (warned.add(key) && logger != null) {
            logger.warning("Ability tuning: invalid " + key + " value '" + raw
                    + "'; using the shipped default");
        }
    }
}
