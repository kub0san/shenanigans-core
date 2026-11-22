package com.hizeric.shenaniganscore.core.config;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * File IO for the config auto-updater: backup before writing, in-memory Configurate round trip,
 * and recovery when an on-disk file cannot be parsed.
 *
 * <p>Never touches runtime data files — the managed file list lives in {@link ConfigAutoUpdater}.</p>
 */
public final class ConfigFiles {

    private ConfigFiles() { }

    /**
     * Updates {@code <dataFolder>/<spec.resourcePath()>}. Returns true when anything was written,
     * including a fresh extraction or an unparseable-file recovery.
     */
    public static boolean update(org.bukkit.plugin.Plugin plugin, ConfigSpec spec, java.io.File dataFolder) {
        if (plugin == null || spec == null || dataFolder == null) return false;
        byte[] bundled = readResource(plugin, spec.resourcePath());
        if (bundled == null) {
            plugin.getLogger().severe("ConfigAutoUpdater: bundled resource '" + spec.resourcePath()
                    + "' is missing; skipping it");
            return false;
        }
        return updateFile(new java.io.File(dataFolder, spec.resourcePath()), bundled, spec, plugin.getLogger());
    }

    /** Same as {@link #update} with explicit file and bundled bytes — the unit-test entry point. */
    public static boolean updateFile(java.io.File file, byte[] bundledBytes, ConfigSpec spec, Logger logger) {
        if (file == null || bundledBytes == null || spec == null) return false;
        Logger log = logger == null ? Logger.getLogger("ConfigFiles") : logger;
        try {
            Files.createDirectories(file.toPath().getParent());
            if (!file.exists()) {
                Files.write(file.toPath(), bundledBytes);
                log.info("ConfigAutoUpdater: extracted " + file.getName());
                return true;
            }
            byte[] original = Files.readAllBytes(file.toPath());
            CommentedConfigurationNode disk;
            try {
                disk = loadFromBytes(original);
            } catch (IOException parseError) {
                java.io.File backup = backup(file);
                log.severe("ConfigAutoUpdater: could not parse " + file.getName()
                        + " — restored the bundled copy (backup: " + backup.getName() + "): "
                        + parseError.getMessage());
                Files.write(file.toPath(), bundledBytes);
                return true;
            }

            CommentedConfigurationNode bundled = loadFromBytes(bundledBytes);
            boolean changed = ConfigMerge.merge(disk, bundled, spec.obsoleteKeys());
            if (!changed) return false;

            try {
                disk.node("config-version").set(spec.version());
            } catch (org.spongepowered.configurate.serialize.SerializationException e) {
                log.warning("ConfigAutoUpdater: could not stamp config-version in " + file.getName());
            }
            backup(file);
            Files.write(file.toPath(), saveToBytes(disk));
            log.info("ConfigAutoUpdater: updated " + file.getName());
            return true;
        } catch (IOException e) {
            log.severe("ConfigAutoUpdater: could not update " + file.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Single rolling backup written before any rewrite. */
    public static java.io.File backup(java.io.File file) throws IOException {
        if (file == null) return null;
        java.io.File backup = new java.io.File(file.getPath() + ".backup");
        Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return backup;
    }

    private static byte[] readResource(org.bukkit.plugin.Plugin plugin, String resourcePath) {
        try (java.io.InputStream in = plugin.getResource(resourcePath)) {
            return in == null ? null : in.readAllBytes();
        } catch (IOException e) {
            plugin.getLogger().warning("ConfigAutoUpdater: could not read bundled " + resourcePath + ": " + e.getMessage());
            return null;
        }
    }

    static CommentedConfigurationNode loadFromBytes(byte[] bytes) throws IOException {
        if (bytes == null) throw new IOException("no bytes to parse");
        return YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(new String(bytes, StandardCharsets.UTF_8))))
                .nodeStyle(org.spongepowered.configurate.yaml.NodeStyle.BLOCK)
                .build()
                .load();
    }

    static byte[] saveToBytes(CommentedConfigurationNode node) throws IOException {
        StringWriter out = new StringWriter();
        YamlConfigurationLoader.builder()
                .sink(() -> new BufferedWriter(out))
                .nodeStyle(org.spongepowered.configurate.yaml.NodeStyle.BLOCK)
                .build()
                .save(node);
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
