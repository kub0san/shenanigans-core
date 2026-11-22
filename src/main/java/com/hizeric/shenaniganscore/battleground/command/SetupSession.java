package com.hizeric.shenaniganscore.battleground.command;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.arena.ArenaRegion;
import com.hizeric.shenaniganscore.battleground.arena.WorldEditBridge;
import com.hizeric.shenaniganscore.battleground.arena.WorldEditSupport;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * One-command arena setup. Runs one session per admin; if the WorldEdit
 * selection is not ready yet it hands out the wand and watches for the two
 * corners, then completes region and snapshot by itself.
 */
public final class SetupSession {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final Map<UUID, SetupSession> ACTIVE = new ConcurrentHashMap<>();
    private static final int WATCH_TICKS = 10;
    private static final int TTL_TICKS = 20 * 120;

    public enum Phase { AWAITING_SELECTION, APPLYING, CAPTURING, DONE, CANCELLED }

    private final ShenanigansCore plugin;
    private final Player admin;
    private final UUID id;
    private volatile Phase phase = Phase.AWAITING_SELECTION;
    private ScheduledTask watcher;
    private int elapsed;

    private SetupSession(ShenanigansCore plugin, Player admin) {
        this.plugin = plugin;
        this.admin = admin;
        this.id = admin.getUniqueId();
    }

    /** @return true when setup finished in this call or a watcher was started */
    public static boolean start(ShenanigansCore plugin, Player admin) {
        if (ACTIVE.containsKey(admin.getUniqueId())) {
            say(admin, "<yellow>Setup is already running for you. Select both corners to finish it.");
            return false;
        }
        if (!WorldEditSupport.isAvailable()) {
            say(admin, "<red>No WorldEdit API provider found (WorldEdit or FastAsyncWorldEdit).");
            return false;
        }

        SetupSession session = new SetupSession(plugin, admin);
        ACTIVE.put(admin.getUniqueId(), session);
        if (WorldEditBridge.selection(admin) != null) {
            session.apply();
            return true;
        }

        session.phase = Phase.AWAITING_SELECTION;
        if (!dispatchWand(admin)) {
            say(admin, "<yellow>Could not hand you the wand automatically — run <white>// wand"
                    + "<yellow>, select two corners, and setup will finish on its own.");
        } else {
            say(admin, "<aqua><bold>Setup<reset> <gray>— select two corners with the wand; "
                    + "region and snapshot are saved automatically. (2 min)");
        }
        // Folia: player commands run on the player's region; the tick reads player state
        // (WorldEdit selection) and must stay region-safe — use the entity scheduler.
        session.watcher = admin.getScheduler()
                .runAtFixedRate(plugin, task -> session.tick(), () -> cancel(session.id),
                        WATCH_TICKS, WATCH_TICKS);
        return true;
    }

    /** Cancel a running setup (manual capture, restore, quit, shutdown). */
    public static void cancel(UUID playerId) {
        SetupSession session = ACTIVE.remove(playerId);
        if (session != null) session.stop();
    }

    public static void cancelAll() {
        for (UUID id : ACTIVE.keySet()) cancel(id);
    }

    public static boolean isActive(UUID playerId) {
        return ACTIVE.containsKey(playerId);
    }

    public Phase phase() {
        return phase;
    }

    private void tick() {
        if (!admin.isOnline()) {
            cancel(id);
            return;
        }
        elapsed += WATCH_TICKS;
        if (elapsed >= TTL_TICKS) {
            say(admin, "<red>Setup timed out — no selection detected. Run <white>/bg setup<red> again.");
            cancel(id);
            return;
        }
        if (WorldEditBridge.selection(admin) != null) {
            apply();
        }
    }

    private void apply() {
        // Guard against a second apply while the capture is already scheduled (tick() can fire
        // while the region task is still queued — the old synchronous code could not re-enter).
        if (phase != Phase.AWAITING_SELECTION) return;
        phase = Phase.APPLYING;
        var manager = plugin.getBattlegroundManager();
        if (!WorldEditBridge.applySelection(manager, admin)) {
            say(admin, "<red>Could not read the WorldEdit selection. Select two corners and try again.");
            cancel(id);
            return;
        }
        phase = Phase.CAPTURING;
        ArenaRegion region = manager.arenaStore().region();
        Location anchor = region != null ? region.center() : null;
        if (anchor != null) {
            // capture() reads the arena's blocks — dispatch to the arena's region, not to the
            // player's/watcher's region (Folia latent bug; a single-region Paper hides it).
            Bukkit.getRegionScheduler().execute(plugin, anchor, () -> reportCapture(manager));
        } else {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> reportCapture(manager));
        }
    }

    private void reportCapture(BattlegroundManager manager) {
        boolean captured = manager.capture();
        // Report back on the admin's entity scheduler — where the reply helpers are safe.
        admin.getScheduler().run(plugin, task -> {  // retired-callback null: keep the task
            if (!captured) {
                say(admin, "<red>Region saved, but the snapshot failed - see console.");
                cancel(id);
                return;
            }
            phase = Phase.DONE;
            stop();
            ACTIVE.remove(id);
            say(admin, "<green><bold>Arena ready.<reset> <gray>Region and snapshot saved.");
        }, null); // no retired callback: the admin finishing the setup is not a failure case
    }

    private void stop() {
        if (watcher != null) {
            watcher.cancel();
            watcher = null;
        }
        phase = Phase.CANCELLED;
    }

    /** FAWE/WorldEdit register "wand" as a subcommand, not a label — try label+argument forms first. */
    private static boolean dispatchWand(Player player) {
        String[] commands = {"worldedit wand", "// wand", "worldedit:wand", "fawe:wand", "wand", "//wand"};
        boolean loggedFirst = false;
        for (String command : commands) {
            try {
                if (Bukkit.dispatchCommand(player, command)) return true;
            } catch (RuntimeException e) {
                // CommandException (unknown spelling) is a RuntimeException; log the first
                // failure at FINE and keep trying. Errors (OOM, LinkageError, …) propagate.
                if (!loggedFirst) {
                    Bukkit.getLogger().log(Level.FINE, "wand dispatch failed for spelling: " + command, e);
                    loggedFirst = true;
                }
            }
        }
        return false;
    }

    private static void say(Player player, String message) {
        player.sendMessage(MM.deserialize(message));
    }
}
