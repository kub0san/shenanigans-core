package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Radius metadata and hidden catalogue definition for Naoya Zenin's Time Cell Moon Palace. The
 * command entry point was removed in the command-system rewrite (ticket 15); the domain now has no
 * activation command of its own. The bounds stay so a future activation path has a single source
 * for the default and the cap.
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:time_cell_moon_palace");
    public static final int PRICE = 0;
    public static final int DEFAULT_COOLDOWN_SECONDS = 240;

    public static final String PERMISSION = "shenaniganscore.use";

    private static final double DEFAULT_RADIUS = 32.0;
    private static final double DEFAULT_MAX_RADIUS = 48.0;

    /** Below this the stage (path, eye, lobes) no longer fits inside the sphere. */
    public static final double MIN_RADIUS = 12.0;

    public static double getDefaultRadius() {
        return DEFAULT_RADIUS;
    }

    /**
     * This is a closed barrier: a real fake-block shell is drawn at the radius, so the
     * cap is much lower than the shrine's open radius. Used for any future radius argument.
     */
    public static double getMaxRadius() {
        return Math.max(MIN_RADIUS, DEFAULT_MAX_RADIUS);
    }

    public static int getCooldownSeconds() {
        return DEFAULT_COOLDOWN_SECONDS;
    }

    /** Hidden: castable from the debug path, never sold, bound or listed. */
    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_purple>Domain Expansion: <light_purple>Time Cell Moon Palace"),
                Material.PINK_STAINED_GLASS,
                PRICE,
                Ability::getCooldownSeconds,
                1.0,
                (player, power) -> cast(player),
                player -> false,
                true);
    }

    private static void cast(Player player) {
        Session session = new Session(player, DEFAULT_RADIUS);
        if (!ShenanigansCore.getInstance().getSessionManager().startSession(session)) return;
        session.start();
    }
}