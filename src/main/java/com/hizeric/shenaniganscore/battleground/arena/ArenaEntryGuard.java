package com.hizeric.shenaniganscore.battleground.arena;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.command.Permissions;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps everyone who is not in the battleground out of the arena.
 *
 * <p>The mode-only entrance is the hub: {@code /bg join} stages the player, and the
 * DEPLOY button is the only path into the arena. This is the backstop for everything
 * else — a teleport plugin, {@code /back} after a rejoin, an ender pearl — by matching
 * each move against the arena footprint instead of hardcoding specific commands.</p>
 *
 * <p>The plugin's own teleports (staging, deploy, return-to-arena, quit-home) are issued
 * after {@link #allowOnce(Player)}, which hands out a short-lived token that authorises
 * exactly one teleport.</p>
 */
public final class ArenaEntryGuard implements Listener {

    private static final ArenaEntryGuard INSTANCE = new ArenaEntryGuard();

    /** The staging anchor sits above the arena; cover it so nobody is parked inside. */
    private static final int STAGING_ANCHOR_MARGIN = 40;
    /** A little slack below the arena so the structure's footprint is fully protected. */
    private static final int BELOW_ARENA_MARGIN = 16;

    private final Set<UUID> internalTransit = ConcurrentHashMap.newKeySet();

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
        if (uuid != null) internalTransit.add(uuid);
    }

    /**
     * Consumes a token on the first teleport, whatever its destination. A token must
     * never survive an outward teleport and authorise a later, unrelated entry.
     */
    boolean claimTransit(UUID uuid) {
        return uuid != null && internalTransit.remove(uuid);
    }

    /** Test/inspection hook: whether a token is currently pending. */
    public boolean hasTransit(UUID uuid) {
        return uuid != null && internalTransit.contains(uuid);
    }

    /** Drops any pending token; used on quit so a stale token never authorises a later teleport. */
    public void clearTransit(UUID uuid) {
        if (uuid != null) internalTransit.remove(uuid);
    }

    // ------------------------------------------------------------ guard

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onTeleport(PlayerTeleportEvent event) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null) return;
        ArenaRegion region = plugin.getBattlegroundManager().arenaStore().region();
        if (region == null) return;
        Location destination = event.getTo();
        if (destination == null) return;
        Player player = event.getPlayer();
        // Spend the token on this teleport before deciding whether the destination is
        // guarded: an outward teleport must not leave a token for a later inward one.
        boolean internal = claimTransit(player.getUniqueId());
        if (!inside(region, destination)) return; // nothing to guard

        if (allowed(plugin, player, region, internal)) return;

        event.setCancelled(true);
        message(player, "<red>You can only get into the arena by joining the battleground.");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onMove(PlayerMoveEvent event) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null) return;
        ArenaRegion region = plugin.getBattlegroundManager().arenaStore().region();
        if (region == null) return;
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || from == null) return;
        // Only horizontal movement matters; looking around must never snap the player.
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()) return;
        if (inside(region, from)) return; // already inside: the mode owns the exit path
        if (!inside(region, to)) return;

        Player player = event.getPlayer();
        if (allowed(plugin, player, region, false)) return;

        event.setTo(from);
        message(player, "<red>You can only get into the arena by joining the battleground.");
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onRespawn(PlayerRespawnEvent event) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin == null) return;
        ArenaRegion region = plugin.getBattlegroundManager().arenaStore().region();
        if (region == null) return;
        if (!inside(region, event.getRespawnLocation())) return;

        Player player = event.getPlayer();
        if (allowed(plugin, player, region, false)) return;

        Location destination = RespawnTarget.resolve(
                region.spawn(), player.getWorld().getSpawnLocation(), region);
        event.setRespawnLocation(destination);
        message(player, "<red>You can only get into the arena by joining the battleground.");
    }

    // ------------------------------------------------------------ helpers

    private boolean allowed(ShenanigansCore plugin, Player player, ArenaRegion region, boolean internal) {
        BattlegroundManager manager = plugin.getBattlegroundManager();
        UUID uuid = player.getUniqueId();
        return ArenaEntryDecision.evaluate(new ArenaEntryDecision.Input(
                true,
                manager.isDeployed(uuid),
                manager.staging().isStaged(player),
                player.hasPermission(Permissions.BG_ADMIN),
                internal)) == ArenaEntryDecision.Verdict.ALLOW;
    }

    private static boolean inside(ArenaRegion region, Location destination) {
        if (region == null || destination == null) return false;
        World world = region.world();
        if (world == null) return false;
        if (destination.getWorld() == null || !destination.getWorld().equals(world)) return false;
        int x = destination.getBlockX();
        int z = destination.getBlockZ();
        if (x < region.min().getBlockX() || x > region.max().getBlockX()) return false;
        if (z < region.min().getBlockZ() || z > region.max().getBlockZ()) return false;
        int y = destination.getBlockY();
        return y >= region.min().getBlockY() - BELOW_ARENA_MARGIN
                && y <= region.max().getBlockY() + STAGING_ANCHOR_MARGIN;
    }

    private static void message(Player player, String miniMessage) {
        player.sendMessage(MiniMessage.miniMessage().deserialize(miniMessage));
    }
}
