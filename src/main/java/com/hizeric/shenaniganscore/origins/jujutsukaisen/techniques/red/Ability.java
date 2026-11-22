package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.red;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;

/**
 * Catalogue definition for Gojo's <b>Cursed Technique Reversal: Red</b> (赫).
 *
 * <p>The orb lives in {@link Projectile} (timing, collision, repulsion) and {@link Builder}
 * (every visual); gating is centralised in {@code core.ability.AbilityCaster}.</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:red");
    public static final int PRICE = 120;
    public static final int DEFAULT_COOLDOWN_SECONDS = 40;

    /** Fixed power of the standard cast. */
    public static final double DEFAULT_POWER = 1.0;

    public static int getCooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(TECHNIQUES, "red", DEFAULT_COOLDOWN_SECONDS);
    }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_red>Cursed Technique Reversal: Red"),
                Material.BLAZE_ROD,
                PRICE,
                Ability::getCooldownSeconds,
                DEFAULT_POWER,
                (player, power) -> perform(player, power),
                player -> ShenanigansCore.getInstance().getSessionManager()
                        .hasActiveSession(player.getUniqueId()),
                false
        );
    }

    public static void perform(Player player, double power) {
        new Projectile(player, power).start();
        // The 赫 title lands when the orb is released.
        player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>Cursed Technique Reversal..."));
    }
}
