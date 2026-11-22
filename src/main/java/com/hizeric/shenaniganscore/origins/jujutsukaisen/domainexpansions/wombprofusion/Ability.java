package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Radius metadata and hidden catalogue definition for the Womb Profusion domain expansion.
 *
 * <p>The domain implementation ({@link Session} and its helpers) is present. It now has a hidden
 * catalogue definition — castable from the debug path but never sold, bound or listed. There is no
 * standalone activation command; these constants stay put so a future path has a single source of
 * truth.</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:womb_profusion");
    public static final int PRICE = 0;

    /** Permission gate a future activation path should require. */
    public static final String PERMISSION = "shenaniganscore.use";

    /** Default radius. */
    public static final double DEFAULT_RADIUS = 40.0;
    /** Hard cap. */
    public static final double MAX_RADIUS = 80.0;
    /** Below this the tower and carpet no longer fit. */
    public static final double MIN_RADIUS = 20.0;

    /** Cooldown for a future activation path, in seconds. */
    public static final int DEFAULT_COOLDOWN_SECONDS = 45;

    public static double getDefaultRadius() {
        return DEFAULT_RADIUS;
    }

    public static double getMaxRadius() {
        return MAX_RADIUS;
    }

    public static int getCooldownSeconds() {
        return DEFAULT_COOLDOWN_SECONDS;
    }

    /** Hidden: castable from the debug path, never sold, bound or listed. */
    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_purple>Domain Expansion: <light_purple>Womb Profusion"),
                Material.NETHER_WART_BLOCK,
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
