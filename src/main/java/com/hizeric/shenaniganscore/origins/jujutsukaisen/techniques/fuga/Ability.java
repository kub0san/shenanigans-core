package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.fuga;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;

/**
 * Catalogue definition for Sukuna's Fire Arrow (<b>Fuga</b>).
 *
 * <p>The arrow lives in {@link Projectile} (windup, flight, detonation) and {@link Builder}
 * (every visual).</p>
 *
 * <p><b>Domain rule.</b> A technique normally cannot be cast from inside a domain — except inside
 * Sukuna's own live Malevolent Shrine, where Fuga is the finisher. That exception is carried on
 * this ability's definition, not hardcoded into the shared caster.</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:fuga");
    public static final int PRICE = 200;
    public static final int DEFAULT_COOLDOWN_SECONDS = 75;

    /** Fixed power of the standard cast. */
    public static final double DEFAULT_POWER = 1.0;

    public static int getCooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(TECHNIQUES, "fuga", DEFAULT_COOLDOWN_SECONDS);
    }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_red>Fire Arrow: Fuga"),
                Material.ECHO_SHARD,
                PRICE,
                Ability::getCooldownSeconds,
                DEFAULT_POWER,
                (player, power) -> perform(player, power),
                player -> {
                    ShenanigansCore core = ShenanigansCore.getInstance();
                    return core.getSessionManager().hasActiveSession(player.getUniqueId())
                            && Projectile.findShrine(player) == null;
                },
                false
        );
    }

    /** Draws the bow: the projectile drives its own windup, flight and detonation. */
    public static void perform(Player player, double power) {
        new Projectile(player, power).start();
        // The "Open" (解) call-out comes when the arrow is released.
        player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>Fuga..."));
    }
}
