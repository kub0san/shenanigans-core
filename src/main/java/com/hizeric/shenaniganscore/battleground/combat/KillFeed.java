package com.hizeric.shenaniganscore.battleground.combat;

import com.hizeric.shenaniganscore.ShenanigansCore;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;

/** Chat kill feed and streak announcements. */
public final class KillFeed {

    private KillFeed() { }

    public static void kill(String killer, String victim, int credits, int streak) {
        Bukkit.broadcast(MiniMessage.miniMessage().deserialize(
                "<dark_gray>[Kill] <white>" + killer + " <gray>➤ <white>" + victim
                        + " <yellow>+" + credits + "c"
                        + (streak >= 3 ? " <red><bold>" + streak + " kill streak!" : "")));
    }

    public static void info(String message) {
        ShenanigansCore core = ShenanigansCore.getInstance();
        if (core != null) core.getLogger().info("[Battleground] " + message);
    }
}
