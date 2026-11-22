package com.hizeric.shenaniganscore.core.sound;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;

public final class SoundUtils {

    private SoundUtils() {}

    public static void play(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() != null) {
            loc.getWorld().playSound(loc, sound, SoundCategory.MASTER, volume, pitch);
        }
    }

    public static void playPlayer(org.bukkit.entity.Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
    }
}
