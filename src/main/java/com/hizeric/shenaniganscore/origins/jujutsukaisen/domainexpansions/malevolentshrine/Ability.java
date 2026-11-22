package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Radius metadata and hidden catalogue definition for the Malevolent Shrine domain expansion. The
 * command entry point was removed in the command-system rewrite (ticket 15); the domain now has no
 * activation command of its own. These values stay put — the Brigadier bounds used to read them
 * and {@code HardcodedDefaultsTest} pins them.
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:malevolent_shrine");
    public static final int PRICE = 0;
    public static final int DEFAULT_COOLDOWN_SECONDS = 300;

    /** Permission gate a future activation path should require. */
    public static final String PERMISSION = "shenaniganscore.use";

    /** Default radius: 1.25x the closed domains' 48m cap. */
    public static final double DEFAULT_RADIUS = 60.0;

    /**
     * Hard cap: 1.5x the closed domains' 48m cap (72m). The shrine is the only open domain,
     * so it stays wider than a closed one — the old 200m lore cap is gone.
     */
    public static final double MAX_RADIUS = 72.0;
    public static final double MIN_RADIUS = 1.0;

    public static double getDefaultRadius() {
        return DEFAULT_RADIUS;
    }

    public static int getCooldownSeconds() {
        return DEFAULT_COOLDOWN_SECONDS;
    }

    /** Hidden: castable from the debug path, never sold, bound or listed. */
    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_purple>Domain Expansion: <light_purple>Malevolent Shrine"),
                Material.SKELETON_SKULL,
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