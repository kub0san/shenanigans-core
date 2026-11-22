package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * The ONLY class that references WorldEdit types. Never touched unless
 * {@link WorldEditSupport#isAvailable()} returned true.
 */
public final class WorldEditBridge {

    /**
     * Cleared by the plugin's disable path before FAWE unloads. A late scheduled tick must be a
     * no-op rather than reaching WorldEdit's relighter after the provider has gone away.
     */
    private static volatile boolean enabled = true;

    private WorldEditBridge() { }

    public static void setEnabled(boolean value) { enabled = value; }

    private static boolean disabled() { return !enabled; }

    public static Region selection(Player player) {
        if (disabled() || player == null) return null;
        try {
            com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
            com.sk89q.worldedit.LocalSession session =
                    WorldEdit.getInstance().getSessionManager().get(actor);
            // WorldEdit selections are per-world and the selection world is NOT
            // guaranteed to be the one the player currently stands in. Passing the
            // player's current world to getSelection(World) throws
            // IncompleteRegionException whenever the two differ, which made a
            // perfectly good selection read as "incomplete". Ask the session which
            // world actually holds the selection (WorldEdit's own documented way).
            com.sk89q.worldedit.world.World world = session.getSelectionWorld();
            if (world == null) {
                world = BukkitAdapter.adapt(player.getWorld());
            }
            Region region = session.getSelection(world);
            if (region == null) {
                ShenanigansCore.getInstance().getLogger().warning(
                        "WorldEdit selection read as null for " + player.getName()
                        + " (selection world: " + world.getName() + ").");
            }
            return region;
        } catch (IncompleteRegionException e) {
            return null;
        }
    }

    public static Clipboard capture(Region region) {
        if (disabled() || region == null) return null;
        EditSession session = editSession(region.getWorld());
        Clipboard clipboard = new BlockArrayClipboard(region);
        ForwardExtentCopy copy = new ForwardExtentCopy(
                session, region, clipboard, region.getMinimumPoint());
        try {
            Operations.completeLegacy(copy);
        } catch (MaxChangedBlocksException e) {
            throw new IllegalStateException("Arena snapshot exceeded WorldEdit's block limit", e);
        }
        session.flushSession();
        return clipboard;
    }

    public static com.sk89q.worldedit.world.World adapt(World bukkitWorld) {
        if (disabled() || bukkitWorld == null) return null;
        return BukkitAdapter.adapt(bukkitWorld);
    }

    public static org.bukkit.util.BlockVector adapt(com.sk89q.worldedit.math.BlockVector3 position) {
        if (disabled() || position == null) return null;
        return new org.bukkit.util.BlockVector(position.getX(), position.getY(), position.getZ());
    }

    /** Applies the player's current WorldEdit selection to the arena store. */
    public static boolean applySelection(com.hizeric.shenaniganscore.battleground.BattlegroundManager manager,
                                         Player player) {
        if (disabled()) return false;
        Region selection = selection(player);
        if (selection == null) return false;
        manager.arenaStore().setRegion(player.getWorld(),
                adapt(selection.getMinimumPoint()),
                adapt(selection.getMaximumPoint()));
        return true;
    }

    public static EditSession editSession(World bukkitWorld) {
        if (disabled() || bukkitWorld == null) return null;
        return editSession(BukkitAdapter.adapt(bukkitWorld));
    }

    public static EditSession editSession(com.sk89q.worldedit.world.World world) {
        if (disabled() || world == null) return null;
        return WorldEdit.getInstance().getEditSessionFactory().getEditSession(world, -1);
    }

    /**
     * Resolves a Sponge-schematic format across WorldEdit flavours.
     *
     * <p>Upstream WorldEdit 7.2 registers the Sponge format under alias {@code sponge};
     * FAWE 2.x renamed its built-ins and registers Sponge v3 as {@code sponge.3}
     * (also {@code slow}/{@code safe}) — a plain {@code sponge} alias no longer exists,
     * so {@code findByAlias("sponge")} returned null and every arena capture failed.
     * Trying the known aliases makes this work with either provider. Alias strings are
     * used deliberately (not FAWE-only enum constants like SPONGE_V3_SCHEMATIC) because
     * this plugin is compiled against upstream WorldEdit 7.2.15, where those constants
     * do not exist and would fail at runtime with NoSuchFieldError.</p>
     */
    private static ClipboardFormat resolveSchematicFormat() {
        for (String alias : new String[] {"sponge.3", "sponge", "schem", "slow", "safe"}) {
            ClipboardFormat format = ClipboardFormats.findByAlias(alias);
            if (format != null) return format;
        }
        return null;
    }

    public static void save(Clipboard clipboard, File file) throws IOException {
        if (disabled() || clipboard == null || file == null) return;
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create " + parent);
        }
        ClipboardFormat format = resolveSchematicFormat();
        if (format == null) {
            throw new IOException("No Sponge schematic format available from the WorldEdit provider");
        }
        try (ClipboardWriter writer = format.getWriter(new FileOutputStream(file))) {
            writer.write(clipboard);
        }
    }

    public static Clipboard load(File file) throws IOException {
        if (disabled() || file == null || !file.exists()) return null;
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) format = resolveSchematicFormat();
        if (format == null) {
            throw new IOException("No Sponge schematic format available from the WorldEdit provider");
        }
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            return reader.read();
        }
    }
}
