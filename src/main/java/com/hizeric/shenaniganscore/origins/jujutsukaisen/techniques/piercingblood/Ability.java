package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.piercingblood;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;

/**
 * Catalogue definition for <b>Piercing Blood</b> (穿血, Senketsu), the Blood Manipulation attack.
 *
 * <p>The shot lives in {@link PiercingBloodShot} (timing, hit-scan, damage) and
 * {@link PiercingBloodBuilder} (every visual).</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:piercing_blood");
    public static final int PRICE = 90;
    public static final int DEFAULT_COOLDOWN_SECONDS = 45;

    /** Fixed power of the standard cast. */
    public static final double DEFAULT_POWER = 1.0;

    public static int getCooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(TECHNIQUES, "piercing-blood", DEFAULT_COOLDOWN_SECONDS);
    }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_red>Blood Manipulation: Piercing Blood"),
                Material.PRISMARINE_SHARD,
                PRICE,
                Ability::getCooldownSeconds,
                DEFAULT_POWER,
                (player, power) -> perform(player, power),
                player -> ShenanigansCore.getInstance().getSessionManager()
                        .hasActiveSession(player.getUniqueId()),
                false,
                player -> { if (com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities.isTechniqueFxEnabled()) com.hizeric.shenaniganscore.origins.jujutsukaisen.util.TechniqueFx.burst(player, ID, com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities.isTechniqueFxEnabled()); }
        );
    }

    public static void perform(Player player, double power) {
        new PiercingBloodShot(player, power).start();
        player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>Piercing Blood..."));
    }
}
