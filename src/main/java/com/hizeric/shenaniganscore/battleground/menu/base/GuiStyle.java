package com.hizeric.shenaniganscore.battleground.menu.base;

import org.bukkit.Material;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * GUI palette, loaded from the {@code interface:} block of config.yml. Mirrors
 * PvpCore's StyleManager: a filler pane and an accent tag. Defaults are red; a
 * missing or broken config falls back to them, never to a parse crash.
 */
public final class GuiStyle {
    public static final Material DEFAULT_GLASS = Material.RED_STAINED_GLASS_PANE;
    public static final String DEFAULT_ACCENT = "<#e06c75>";

    private static volatile Material glass = DEFAULT_GLASS;
    private static volatile String accent = DEFAULT_ACCENT;

    private GuiStyle() {
    }

    public static Material glass() {
        return glass;
    }

    /** MiniMessage colour tag, e.g. {@code <#e06c75>} — never includes markup boundaries beyond the tag. */
    public static String accent() {
        return accent;
    }

    /** Applies the bundled defaults. */
    public static void reset() {
        glass = DEFAULT_GLASS;
        accent = DEFAULT_ACCENT;
    }

    /** Reads {@code <dataFolder>/config.yml}; missing/blank keys keep the defaults. */
    public static void apply(File dataFolder) {
        apply(dataFolder, Logger.getLogger(GuiStyle.class.getName()));
    }

    /**
     * Reads the palette, logging why defaults were used. A missing file is an INFO (normal on
     * first boot); a file that exists but cannot be parsed is a WARNING with the cause.
     */
    public static void apply(File dataFolder, Logger logger) {
        if (dataFolder == null) {
            if (logger != null) logger.info("No data folder — using default GUI palette");
            reset();
            return;
        }
        File file = new File(dataFolder, "config.yml");
        if (!file.exists()) {
            if (logger != null) logger.info("config.yml not found — using default GUI palette");
            reset();
            return;
        }
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .source(() -> new BufferedReader(new StringReader(read(file))))
                .build();
        try {
            CommentedConfigurationNode root = loader.load();
            String material = text(root.node("interface", "glass-material").getString());
            String tag = text(root.node("interface", "main-color").getString());
            apply(material, tag);
        } catch (IOException | RuntimeException e) {
            // On any failure keep the red defaults — never ship a broken palette.
            if (logger != null) logger.log(Level.WARNING,
                    "Could not read interface palette from config.yml — using defaults", e);
            reset();
        }
    }

    /** Pure seam used by tests and by {@link #apply(File)}. */
    public static void apply(String glassMaterial, String mainColor) {
        glass = parseMaterial(glassMaterial);
        accent = parseAccent(mainColor);
    }

    private static Material parseMaterial(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_GLASS;
        try {
            Material value = Material.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            return value == null ? DEFAULT_GLASS : value;
        } catch (IllegalArgumentException e) {
            return DEFAULT_GLASS;
        }
    }

    private static String parseAccent(String raw) {
        String tag = text(raw);
        if (tag == null) return DEFAULT_ACCENT;
        if (!tag.startsWith("<") || !tag.endsWith(">") || tag.indexOf('>') != tag.length() - 1) {
            return DEFAULT_ACCENT;
        }
        return tag;
    }

    private static String text(String raw) {
        return raw == null || raw.isBlank() ? null : raw.trim();
    }

    private static String read(File file) throws IOException {
        if (file == null || !file.exists()) return "";
        return Files.readString(file.toPath());
    }
}
