package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class IndicatorRouter {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ShenanigansCore plugin;
    private final HudController controller;

    public IndicatorRouter(ShenanigansCore plugin) {
        this.plugin = plugin;
        this.controller = new HudController(plugin);
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

    public void forget(UUID playerId) {
        controller.forget(playerId);
    }

    public HudController controller() {
        return controller;
    }
}