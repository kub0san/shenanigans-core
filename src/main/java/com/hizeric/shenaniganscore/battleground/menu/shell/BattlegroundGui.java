package com.hizeric.shenaniganscore.battleground.menu.shell;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiChatPrompt;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSession;
import com.hizeric.shenaniganscore.battleground.menu.base.GuiSessions;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** The shared GUI session: owns staged editor values for the screens that stage them. */
public final class BattlegroundGui implements GuiSession {
    private final ShenanigansCore plugin;
    private final Map<UUID, Map<String, Object>> staged = new ConcurrentHashMap<>();

    public BattlegroundGui(ShenanigansCore plugin) {
        this.plugin = plugin;
    }

    public void register() {
        GuiSessions.set(this);
    }

    @Override
    public ShenanigansCore plugin() {
        return plugin;
    }

    @Override
    public void flushOpenEditor(Player player) {
        // No-op: GuiScreen.show() calls this hook before every screen (re)show. The remaining
        // editors save through their own controls, so there is nothing to flush here — and the
        // staged values must survive a re-show or an in-progress edit would be lost.
    }

    @Override
    public Map<UUID, Map<String, Object>> stagedStore() {
        return staged;
    }

    /** Drops every staged screen value for a quitting player. */
    public void forget(Player player) {
        staged.remove(player.getUniqueId());
    }

    public void shutdown() {
        staged.clear();
        GuiChatPrompt.clear();
        GuiSessions.clear();
    }
}
