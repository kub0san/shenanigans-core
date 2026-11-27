package com.hizeric.shenaniganscore.origins.jujutsukaisen.util;

import com.hizeric.shenaniganscore.core.ability.AbilityId;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;

/**
 * JJK-origin cast flourish palette. Matches by full {@link AbilityId}, never by substring,
 * so a Minecraft ability whose id happens to contain "blue" cannot receive JJK FX.
 */
public final class TechniqueFx {

    private record Palette(Particle particle, Particle.DustOptions dust, float pitch) { }

    private static final Map<AbilityId, Palette> PALETTES = Map.of(
            AbilityId.of("jjk:blue"), new Palette(Particle.END_ROD,
                    new Particle.DustOptions(Color.fromRGB(110, 168, 217), 1.4f), 1.1f),
            AbilityId.of("jjk:red"), new Palette(Particle.FLAME,
                    new Particle.DustOptions(Color.fromRGB(224, 108, 117), 1.4f), 1.1f),
            AbilityId.of("jjk:purple"), new Palette(Particle.PORTAL,
                    new Particle.DustOptions(Color.fromRGB(180, 120, 210), 1.4f), 1.1f),
            AbilityId.of("jjk:fuga"), new Palette(Particle.FLAME,
                    new Particle.DustOptions(Color.fromRGB(255, 140, 60), 1.4f), 1.4f),
            AbilityId.of("jjk:piercing_blood"), new Palette(Particle.CRIMSON_SPORE,
                    new Particle.DustOptions(Color.fromRGB(200, 40, 60), 1.4f), 1.1f)
    );

    private TechniqueFx() { }

    /** Emits a burst when a JJK technique is successfully cast. No-op if disabled or unknown. */
    public static void burst(Player player, AbilityId id, boolean enabled) {
        if (player == null || id == null || !enabled) return;
        Palette palette = PALETTES.get(id);
        if (palette == null) return;

        var loc = player.getLocation().add(0, 1.1, 0);
        var world = player.getWorld();
        world.spawnParticle(palette.particle, loc, 18, 0.55, 0.45, 0.55, 0.06);
        world.spawnParticle(Particle.DUST, loc, 12, 0.7, 0.6, 0.7, palette.dust);
        player.playSound(player, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.5f, palette.pitch);
    }
}