package com.hizeric.shenaniganscore.battleground.menu.base;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/** Minimal GUI feedback sounds, kept local so the foundation has no plugin-specific dependency. */
public final class GuiSounds {
    private GuiSounds() {
    }

    public static void click(Player player) {
        play(player, Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    public static void success(Player player) {
        play(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.4f);
    }

    public static void fail(Player player) {
        play(player, Sound.ENTITY_VILLAGER_NO, 0.6f, 1.0f);
    }

    private static void play(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }
}
