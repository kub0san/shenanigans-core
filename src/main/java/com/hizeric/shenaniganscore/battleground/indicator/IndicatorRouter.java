package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Routes battleground HUD notices to the action bar.
 *
 * <p>Sends are deliberately direct and unthrottled: cooldown notices and sticky
 * hints must reach the player on every call. Suppression during a live domain is
 * the only gate, and it is unchanged by the removal of the unused HUD throttle.</p>
 */
public final class IndicatorRouter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ShenanigansCore plugin;

    public IndicatorRouter(ShenanigansCore plugin) {
        this.plugin = plugin;
    }

    public void send(Player player, String miniMessage) {
        if (plugin.getSessionManager().hasActiveSession(player.getUniqueId())) return;
        player.sendActionBar(MM.deserialize(miniMessage));
    }

    public void send(Player player, Component component) {
        if (plugin.getSessionManager().hasActiveSession(player.getUniqueId())) return;
        player.sendActionBar(component);
    }

    public boolean isSuppressed(UUID playerId) {
        return plugin.getSessionManager().hasActiveSession(playerId);
    }
}
