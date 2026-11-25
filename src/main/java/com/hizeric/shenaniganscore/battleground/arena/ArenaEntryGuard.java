package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Keeps everyone who is not in the battleground out of the arena.
 *
 * <p>The mode-only entrance is the hub: {@code /bg join} stages the player, and the DEPLOY button
 * is the only path into the arena. This is the backstop for everything else (a teleport plugin,
 * {@code /back} after a rejoin, an ender pearl) by matching each move against the arena footprint.</p>
 *
 * <p>The plugin's own teleports are issued after {@link #allowOnce(Player)}, which hands out a
 * token that authorises exactly one teleport.</p>
 *
 * <p>Changes: tokens now <b>expire</b> after {@link #TOKEN_TTL_SECONDS} (the old javadoc said
 * "short-lived" but a token that was never spent stayed valid forever and could later authorise an
 * unrelated entry); denial messages are rate limited instead of spamming every tick a player pushes
 * against the border; per-move checks do the cheap coordinate test before any world lookup;
 * quitting clears a player's token and cooldown. Issue #64 additionally ejects an unauthorised
 * player who is <em>already</em> inside the guarded volume instead of letting them walk or teleport
 * out: the teleport is scheduled off the event, guarded by a one-shot token and a per-player queue
 * so it neither runs inside {@link PlayerMoveEvent} nor double-fires with the deployment exit path.
 * An ejection gets its own rate-limited message, not the ingress denial.</p>
 */
public final class ArenaEntryGuard implements Listener {

    private static final ArenaEntryGuard INSTANCE = new ArenaEntryGuard();

    /** Long enough for an async teleport to finish loading chunks, short enough to be harmless. */
    static final long TOKEN_TTL_SECONDS = 10L;
    private static final long TOKEN_TTL_NANOS = TimeUnit.SECONDS.toNanos(TOKEN_TTL_SECONDS);
    private static final long MESSAGE_COOLDOWN_NANOS = TimeUnit.SECONDS.toNanos(2L);

    /** The staging anchor sits above the arena; cover it so nobody is parked inside. */
    private static final int STAGING_ANCHOR_MARGIN = 40;
    /** A little slack below the arena so the structure's footprint is fully protected. */
    private static final int BELOW_ARENA_MARGIN = 16;

    private static final String DENIED = "<red>You can only get into the arena by joining the battleground.";
    private static final String EJECTED = "<gray>Returned to battleground spawn.";

    /** Player to the nanoTime deadline of their pending token. */
    private final Map<UUID, Long> internalTransit = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDenied = new ConcurrentHashMap<>();
    /** Players with an ejection teleport already queued; prevents a move-tick teleport storm. */
    private final Set<UUID> ejecting = ConcurrentHashMap.newKeySet();

    private ArenaEntryGuard() { }

    public static ArenaEntryGuard get() {
        return INSTANCE;
    }

    public void register(ShenanigansCore plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    // ------------------------------------------------------------ tokens

    /** Authorises exactly one teleport into the arena for this player. */
    public void allowOnce(Player player) {
        allowOnce(player == null ? null : player.getUniqueId());
    }

    public void allowOnce(UUID uuid) {
        if (uuid != null) internalTransit.put(uuid, System.nanoTime() + TOKEN_TTL_NANOS);
    }

    /**
     * Consumes a token on the first teleport, whatever its destination. A token must
     * never survive an outward teleport and authorise a later, unrelated entry.
     *
     * @return true only if a token existed and had not expired
     */
    boolean claimTransit(UUID uuid) {
        if (uuid == null) return false;
        Long deadline = internalTransit.remove(uuid);
        return deadline != null && System.nanoTime() - deadline < 0;
    }

    /** Test/inspection hook: whether an unexpired token is currently pending. */
    public boolean hasTransit(UUID uuid) {
        if (uuid == null) return false;
        Long deadline = internalTransit.get(uuid);
        return deadline != null && System.nanoTime() - deadline < 0;
    }

    /** Drops any pending token; used on quit so a stale token never authorises a later teleport. */
    public void clearTransit(UUID uuid) {
        if (uuid != null) internalTransit.remove(uuid);
    }

    /** Test/inspection hook: whether an ejection teleport is currently queued for this player. */
    public boolean isEjectionPending(UUID uuid) {
        return uuid != null && ejecting.contains(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        internalTransit.remove(uuid);
        lastDenied.remove(uuid);
        ejecting.remove(uuid);
    }

    // ------------------------------------------------------------ guard

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Location destination = event.getTo();
        if (destination == null) return;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        ArenaRegion region = activeRegion(plugin);
        Player player = event.getPlayer();
        // Spend the token on this teleport before deciding whether the destination is
        // guarded: an outward teleport must not leave a token for a later inward one.
        boolean internal = claimTransit(player.getUniqueId());
        if (region == null) return;
        // A teleport out of the arena is not automatically an authorised exit: /back, an ender
        // pearl or another plugin must not let a stranded unauthorised player slip past the guard.
        // Apply the same origin/destination decision as onMove: ALLOW keeps authorised exits (the
        // scheduling token, admins, deployed and staged players), EJECT queues the real exit.
        Location origin = event.getFrom();
        boolean originInside = origin != null && inside(region, origin);
        boolean destinationInside = inside(region, destination);
        ArenaEntryDecision.Verdict verdict = decide(plugin, player, internal,
                originInside, destinationInside);
        if (verdict == ArenaEntryDecision.Verdict.ALLOW) return;

        event.setCancelled(true);
        if (verdict == ArenaEntryDecision.Verdict.EJECT) {
            scheduleEjection(plugin, player, region);
        } else {
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from == null) return; // fail open on missing coordinates
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        ArenaRegion region = activeRegion(plugin);
        if (region == null) return;
        // A change of block matters for ingress/exit, and Y counts too so a player cannot fall in
        // vertically. Looking around within one block must not pin an ordinary player — but a
        // stranded player looking around inside must still be ejectable, so containment is checked
        // before the same-block early return.
        boolean sameBlock = ArenaEntryDecision.sameBlock(from.getBlockX(), from.getBlockY(),
                from.getBlockZ(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
        // Compute both sides before the early return: an unauthorised origin inside the arena is
        // an exit that must be ejected, not an ingress that can simply be ignored.
        boolean toInside = inside(region, to);
        boolean fromInside = inside(region, from);
        if (sameBlock) {
            if (!fromInside) return; // ordinary outside/looking move: no pin, no ejection
        } else if (!toInside && !fromInside) return;

        Player player = event.getPlayer();
        ArenaEntryDecision.Verdict verdict = decide(plugin, player, false, fromInside, toInside);
        if (verdict == ArenaEntryDecision.Verdict.ALLOW) return;

        // DENY (ingress) and EJECT (stranded inside) both pin the player to their origin now;
        // EJECT additionally schedules the real exit off this move event. A same-block look is
        // only ever EJECT, and pinning it would undo the look, so leave its coordinates alone.
        if (!sameBlock) event.setTo(from);
        if (verdict == ArenaEntryDecision.Verdict.EJECT) {
            scheduleEjection(plugin, player, region);
        } else {
            deny(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        ArenaRegion region = activeRegion(plugin);
        if (region == null) return;
        if (!inside(region, event.getRespawnLocation())) return;

        Player player = event.getPlayer();
        if (allowed(plugin, player, false)) return;

        Location destination = RespawnTarget.resolve(
                region.spawn(), player.getWorld().getSpawnLocation(), region);
        event.setRespawnLocation(destination);
        deny(player);
    }

    // ------------------------------------------------------------ join sweep

    /**
     * Called from the join listener <em>after</em> {@code manager().repair(player)} has cleared
     * leaked mode flags. A player whose physical location is still inside the guarded volume is
     * stranded there, so they get the same scheduled ejection a move would trigger.
     */
    public void ejectIfStranded(Player player) {
        if (player == null) return;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null) return;
        BattlegroundManager manager = plugin.getBattlegroundManager();
        if (manager == null) return;
        ArenaRegion region = manager.arenaStore().region();
        if (region == null) return;
        if (!inside(region, player.getLocation())) return;

        UUID uuid = player.getUniqueId();
        if (manager.isDeployed(uuid) || manager.staging().isStaged(player)
                || player.hasPermission(Permissions.BG_ADMIN)) return;

        scheduleEjection(plugin, player, region);
    }

    // ------------------------------------------------------------ helpers

    private static ArenaRegion activeRegion(ShenanigansCore plugin) {
        if (plugin == null) return null;
        BattlegroundManager manager = plugin.getBattlegroundManager();
        return manager == null ? null : manager.arenaStore().region();
    }

    /**
     * Queue a one-shot teleport to the persisted return point. The flag deduplicates the many
     * move events a stranded player fires before the task runs; the transit token lets the
     * resulting (outward) teleport consume the guard's permit without a re-entrant cancel.
     */
    private void scheduleEjection(ShenanigansCore plugin, Player player, ArenaRegion region) {
        UUID uuid = player.getUniqueId();
        if (!ejecting.add(uuid)) return; // already queued: no double teleport
        allowOnce(player);
        ejected(player);
        Location destination = RespawnTarget.resolve(
                region.spawn(), player.getWorld().getSpawnLocation(), region);
        Bukkit.getScheduler().runTask(plugin, () -> {
            ejecting.remove(uuid);
            Player current = Bukkit.getPlayer(uuid);
            if (current == null || !current.isOnline()) return;
            current.teleport(destination);
        });
    }

    private boolean allowed(ShenanigansCore plugin, Player player, boolean internal) {
        return decide(plugin, player, internal, false, true) == ArenaEntryDecision.Verdict.ALLOW;
    }

    private ArenaEntryDecision.Verdict decide(ShenanigansCore plugin, Player player, boolean internal,
                                              boolean originInArena, boolean destinationInArena) {
        BattlegroundManager manager = plugin.getBattlegroundManager();
        UUID uuid = player.getUniqueId();
        return ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(
                destinationInArena,
                originInArena,
                manager.isDeployed(uuid),
                manager.staging().isStaged(player),
                player.hasPermission(Permissions.BG_ADMIN),
                internal));
    }

    /** Pure coordinate check for the guarded volume: the arena plus slack below and above. */
    static boolean withinGuardedVolume(ArenaRegion region, int x, int y, int z) {
        if (x < region.min().getBlockX() || x > region.max().getBlockX()) return false;
        if (z < region.min().getBlockZ() || z > region.max().getBlockZ()) return false;
        return y >= region.min().getBlockY() - BELOW_ARENA_MARGIN
                && y <= region.max().getBlockY() + STAGING_ANCHOR_MARGIN;
    }

    /** Guarded volume: the arena plus slack below and the staging anchor above. */
    private static boolean inside(ArenaRegion region, Location destination) {
        if (destination == null) return false;
        return withinGuardedVolume(region, destination.getBlockX(), destination.getBlockY(),
                destination.getBlockZ()) && region.inWorld(destination.getWorld());
    }

    /** At most one feedback message every two seconds per player, shared by both flavours. */
    private void tell(Player player, String message) {
        long now = System.nanoTime();
        Long last = lastDenied.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_NANOS) return;
        lastDenied.put(player.getUniqueId(), now);
        player.sendMessage(MiniMessage.miniMessage().deserialize(message));
    }

    /** Ingress denial: the arena is only reachable by joining the battleground. */
    private void deny(Player player) {
        tell(player, DENIED);
    }

    /** Ejection feedback: distinct from the ingress denial, and on the same rate limit. */
    private void ejected(Player player) {
        tell(player, EJECTED);
    }
}
