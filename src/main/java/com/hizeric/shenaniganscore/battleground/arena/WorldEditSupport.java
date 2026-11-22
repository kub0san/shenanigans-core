package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.Bukkit;

/**
 * Availability check that contains NO WorldEdit types, so this class can be loaded safely even
 * when the optional dependency is absent. Every other class that mentions WorldEdit is only
 * touched after {@link #isAvailable()} returns true.
 *
 * <p>FastAsyncWorldEdit is a drop-in replacement: it ships the same {@code com.sk89q.worldedit}
 * API classes but registers under a different plugin name. Both are accepted here, and
 * {@link #provider()} reports which one was found for log/command messages.</p>
 */
public final class WorldEditSupport {

    private static final String[] PROVIDERS = {"WorldEdit", "FastAsyncWorldEdit", "FAWE"};

    /** Class visibility cannot change while the plugin runs, so the probe runs once. */
    private static volatile Boolean apiVisible;

    private WorldEditSupport() { }

    public static boolean isAvailable() {
        return provider() != null;
    }

    /**
     * @return the enabled provider's plugin name, or {@code null} if none supplies the API.
     *         Requires both an enabled provider plugin and its API classes on our classpath.
     */
    public static String provider() {
        String enabled = enabledProvider();
        if (enabled == null) return null;
        return apiVisible() ? enabled : null;
    }

    /**
     * @return the installed-and-enabled provider plugin name (WorldEdit, FastAsyncWorldEdit or
     *         FAWE), or {@code null} if none is enabled. Does not check class visibility, so it
     *         distinguishes "not installed" from "installed but not on our classpath".
     */
    public static String enabledProvider() {
        for (String name : PROVIDERS) {
            if (Bukkit.getPluginManager().isPluginEnabled(name)) return name;
        }
        return null;
    }

    /**
     * @return true when the WorldEdit API classes are visible to this plugin's classloader.
     *         Uses {@link Class#forName(String, boolean, ClassLoader)} with {@code initialize=false}
     *         so it never runs static initializers: FAWE's {@code BukkitAdapter} is an enum whose
     *         constructor touches the live WorldEdit singleton, which we must not trigger here.
     */
    public static boolean apiVisible() {
        Boolean cached = apiVisible;
        if (cached != null) return cached;
        boolean visible;
        try {
            ClassLoader cl = WorldEditSupport.class.getClassLoader();
            Class.forName("com.sk89q.worldedit.WorldEdit", false, cl);
            Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", false, cl);
            visible = true;
        } catch (Throwable t) {
            visible = false;
        }
        apiVisible = visible;
        return visible;
    }
}
