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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The ONLY class that references WorldEdit types. Never touched unless
 * {@link WorldEditSupport#isAvailable()} returned true.
 *
 * <p>Changes: edit sessions and file streams are always closed (they leaked when an exception was
 * thrown); schematics are written to a temp file and moved into place so a crash cannot leave a
 * half-written {@code main.schem}; and {@link #applySelection} now stores the region in the world
 * that actually holds the selection, not whichever world the player happens to stand in.</p>
 */
public final class WorldEditBridge {

    /**
     * Cleared by the plugin's disable path before FAWE unloads. A late scheduled tick must be a
     * no-op rather than reaching WorldEdit's relighter after the provider has gone away.
     */
    private static volatile boolean enabled = true;

    /** Resolved once; never caches a miss so a late-loading provider is still found. */
    private static volatile ClipboardFormat cachedFormat;

    private WorldEditBridge() { }

    public static void setEnabled(boolean value) { enabled = value; }

    /** Drops the cached schematic format so a provider reload cannot leave a stale reference. */
    public static void clearCachedFormat() {
        cachedFormat = null;
    }

    private static boolean disabled() { return !enabled; }

    public static Region selection(Player player) {
        if (disabled() || player == null) return null;
        try {
            com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
            com.sk89q.worldedit.LocalSession session =
                    WorldEdit.getInstance().getSessionManager().get(actor);
            // WorldEdit selections are per-world and the selection world is NOT guaranteed to be
            // the one the player currently stands in. Ask the session which world holds it.
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
        com.sk89q.worldedit.world.World world = region.getWorld();
        if (world == null) {
            throw new IllegalStateException("The selection has no world to capture from");
        }
        Clipboard clipboard = new BlockArrayClipboard(region);
        try (EditSession session = editSession(world)) {
            if (session == null) return null;
            ForwardExtentCopy copy = new ForwardExtentCopy(
                    session, region, clipboard, region.getMinimumPoint());
            Operations.completeLegacy(copy);
            session.flushSession();
        } catch (MaxChangedBlocksException e) {
            throw new IllegalStateException("Arena snapshot exceeded WorldEdit's block limit", e);
        }
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

        // Use the world the selection lives in. Using player.getWorld() stored the right
        // coordinates under the wrong world whenever the player had walked away from it.
        World world = player.getWorld();
        com.sk89q.worldedit.world.World selectionWorld = selection.getWorld();
        if (selectionWorld != null) {
            World adapted = BukkitAdapter.adapt(selectionWorld);
            if (adapted != null) world = adapted;
        }
        manager.arenaStore().setRegion(world,
                adapt(selection.getMinimumPoint()),
                adapt(selection.getMaximumPoint()));
        return true;
    }

    /** Caller owns the session and should close it (it is AutoCloseable). */
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
     * <p>Upstream WorldEdit 7.2 registers the Sponge format under alias {@code sponge}; FAWE 2.x
     * registers Sponge v3 as {@code sponge.3} (also {@code slow}/{@code safe}). Alias strings are
     * used deliberately (not FAWE-only enum constants) because this plugin is compiled against
     * upstream WorldEdit 7.2.15, where those constants do not exist.</p>
     */
    private static ClipboardFormat resolveSchematicFormat() {
        ClipboardFormat cached = cachedFormat;
        if (cached != null) return cached;
        for (String alias : new String[] {"sponge.3", "sponge", "schem", "slow", "safe"}) {
            ClipboardFormat format = ClipboardFormats.findByAlias(alias);
            if (format != null) {
                cachedFormat = format;
                return format;
            }
        }
        return null;
    }

    /** Temp file, then move: a crash mid-write leaves the previous schematic intact. */
    public static void save(Clipboard clipboard, File file) throws IOException {
        if (disabled() || clipboard == null || file == null) return;
        Path target = file.toPath().toAbsolutePath();
        Files.createDirectories(target.getParent());
        ClipboardFormat format = resolveSchematicFormat();
        if (format == null) {
            throw new IOException("No Sponge schematic format available from the WorldEdit provider");
        }
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(temp));
                 ClipboardWriter writer = format.getWriter(out)) {
                writer.write(clipboard);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public static Clipboard load(File file) throws IOException {
        if (disabled() || file == null || !file.exists()) return null;
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) format = resolveSchematicFormat();
        if (format == null) {
            throw new IOException("No Sponge schematic format available from the WorldEdit provider");
        }
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file.toPath()));
             ClipboardReader reader = format.getReader(in)) {
            return reader.read();
        }
    }
}
