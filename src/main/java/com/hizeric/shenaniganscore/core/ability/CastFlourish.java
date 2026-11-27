package com.hizeric.shenaniganscore.core.ability;

import org.bukkit.entity.Player;

/**
 * Origin-owned cosmetic hook played after a successful, non-debug cast.
 *
 * <p>Core only knows this neutral seam; each origin supplies its own implementation
 * (palette, particles, sounds). A flourish failure is cosmetic and must not undo the
 * cast or release its cooldown.</p>
 */
@FunctionalInterface
public interface CastFlourish {
    CastFlourish NONE = player -> { };

    void play(Player player);
}