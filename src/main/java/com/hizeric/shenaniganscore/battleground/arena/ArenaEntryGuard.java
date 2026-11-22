package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
 * against the border; per-move checks do the cheap coordinate test before any world lookup; and
 * quitting clears a player's token and cooldown.</p>
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

    /** Player to the nanoTime deadline of their pending token. */
    private final Map<UUID, Long> internalTransit = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDenied = new ConcurrentHashMap<>();

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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        internalTransit.remove(uuid);
        lastDenied.remove(uuid);
    }

    // ------------------------------------------------------------ guard

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        Location destination = event.getTo();
        if (destination == null) return;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        ArenaRegion region = activeRegion(plugin);
        Player player = event.getPlayer();
        // Spend the token on this teleport before deciding whether the destination is
        // guarded: an outward teleport must not leave a token for a later inward one.
        boolean internal = claimTransit(player.getUniqueId());
        if (region == null || !inside(region, destination)) return; // nothing to guard

        if (allowed(plugin, player, internal)) return;

        event.setCancelled(true);
        deny(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null) return;
        // Only horizontal movement matters; looking around must never snap the player.
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;

        ShenanigansCore plugin = ShenanigansCore.getInstance();
        ArenaRegion region = activeRegion(plugin);
        if (region == null) return;
        if (!inside(region, to)) return;   // cheap coordinate test rejects almost every move
        if (inside(region, from)) return;  // already inside: the mode owns the exit path

        Player player = event.getPlayer();
        if (allowed(plugin, player, false)) return;

        event.setTo(from);
        deny(player);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
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

    // ------------------------------------------------------------ helpers

    private static ArenaRegion activeRegion(ShenanigansCore plugin) {
        if (plugin == null) return null;
        BattlegroundManager manager = plugin.getBattlegroundManager();
        return manager == null ? null : manager.arenaStore().region();
    }

    private boolean allowed(ShenanigansCore plugin, Player player, boolean internal) {
        BattlegroundManager manager = plugin.getBattlegroundManager();
        UUID uuid = player.getUniqueId();
        return ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(
                true,
                manager.isDeployed(uuid),
                manager.staging().isStaged(player),
                player.hasPermission(Permissions.BG_ADMIN),
                internal)) == ArenaEntryDecision.Verdict.ALLOW;
    }

    /** Guarded volume: the arena plus slack below and the staging anchor above. */
    private static boolean inside(ArenaRegion region, Location destination) {
        if (destination == null) return false;
        int x = destination.getBlockX();
        int z = destination.getBlockZ();
        if (x < region.min().getBlockX() || x > region.max().getBlockX()) return false;
        if (z < region.min().getBlockZ() || z > region.max().getBlockZ()) return false;
        int y = destination.getBlockY();
        if (y < region.min().getBlockY() - BELOW_ARENA_MARGIN
                || y > region.max().getBlockY() + STAGING_ANCHOR_MARGIN) {
            return false;
        }
        return region.inWorld(destination.getWorld());
    }

    /** At most one denial message every two seconds per player. */
    private void deny(Player player) {
        long now = System.nanoTime();
        Long last = lastDenied.get(player.getUniqueId());
        if (last != null && now - last < MESSAGE_COOLDOWN_NANOS) return;
        lastDenied.put(player.getUniqueId(), now);
        player.sendMessage(MiniMessage.miniMessage().deserialize(DENIED));
    }
}
