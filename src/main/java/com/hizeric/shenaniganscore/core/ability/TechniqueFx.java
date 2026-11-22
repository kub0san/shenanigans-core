package com.hizeric.shenaniganscore.core.ability;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * Small, palette-matched cast flourish shared by every technique. Domain
 * expansions are deliberately excluded — they get their own rewrite.
 */
public final class TechniqueFx {
    private static final Map<String, Particle> PARTICLES = Map.of(
            "blue", Particle.END_ROD,
            "red", Particle.FLAME,
            "purple", Particle.PORTAL,
            "fuga", Particle.FLAME,
            "piercing", Particle.CRIMSON_SPORE
    );
    private static final Map<String, Particle.DustOptions> DUST = Map.of(
            "blue", new Particle.DustOptions(Color.fromRGB(110, 168, 217), 1.4f),
            "red", new Particle.DustOptions(Color.fromRGB(224, 108, 117), 1.4f),
            "purple", new Particle.DustOptions(Color.fromRGB(180, 120, 210), 1.4f),
            "fuga", new Particle.DustOptions(Color.fromRGB(255, 140, 60), 1.4f),
            "piercing", new Particle.DustOptions(Color.fromRGB(200, 40, 60), 1.4f)
    );

    private TechniqueFx() {
    }

    /** Emits a burst when a non-domain technique is successfully cast. */
    public static void burst(Player player, AbilityId id) {
        if (player == null || id == null) return;
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin != null && !plugin.getConfig().getBoolean("technique-fx.enabled", true)) return;
        String key = techniqueKey(id.value());
        if (key == null) return;
        Particle particle = PARTICLES.get(key);
        if (particle == null) return;

        var loc = player.getLocation().add(0, 1.1, 0);
        var world = player.getWorld();
        world.spawnParticle(particle, loc, 18, 0.55, 0.45, 0.55, 0.06);
        Particle.DustOptions dust = DUST.get(key);
        if (dust != null) {
            world.spawnParticle(Particle.DUST, loc, 12, 0.7, 0.6, 0.7, dust);
        }
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f,
                key.equals("fuga") ? 1.4f : 1.1f);
    }

    /** Blue/red/purple/fuga/piercing → key; anything containing a domain marker → null. */
    private static String techniqueKey(String value) {
        if (value == null) return null;
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("domain") || v.contains("shrine") || v.contains("void")
                || v.contains("timecell") || v.contains("moonpalace")) return null;
        for (String key : PARTICLES.keySet()) {
            if (v.contains(key)) return key;
        }
        return null;
    }
}
