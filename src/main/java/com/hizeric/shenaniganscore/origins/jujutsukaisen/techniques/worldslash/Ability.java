package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.worldslash;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;

/**
 * Catalogue definition for Sukuna's <b>World Cutting Slash</b> (世界ヲ断ツ斬撃).
 *
 * <p>The cut lives in {@link WorldSlashCast} (telegraph, cut planes, damage) and
 * {@link WorldSlashBuilder} (every visual). Gating is centralised in
 * {@code core.ability.AbilityCaster}; this class only supplies the definition and the rule that a
 * technique cannot be cast from inside a domain — except Sukuna's own live Malevolent Shrine,
 * where the cut covers the whole domain.</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:world_slash");
    public static final int PRICE = 250;
    public static final int DEFAULT_COOLDOWN_SECONDS = 90;

    /** Fixed power of the standard cast. */
    public static final double DEFAULT_POWER = 1.0;

    public static int getCooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(TECHNIQUES, "world-slash", DEFAULT_COOLDOWN_SECONDS);
    }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<dark_red>World Cutting Slash"),
                Material.NETHERITE_SCRAP,
                PRICE,
                Ability::getCooldownSeconds,
                DEFAULT_POWER,
                (player, power) -> perform(player, power),
                player -> {
                    ShenanigansCore core = ShenanigansCore.getInstance();
                    return core.getSessionManager().hasActiveSession(player.getUniqueId())
                            && WorldSlashCast.findShrine(player) == null;
                },
                false,
                player -> { if (com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities.isTechniqueFxEnabled()) com.hizeric.shenaniganscore.origins.jujutsukaisen.util.TechniqueFx.burst(player, ID, com.hizeric.shenaniganscore.origins.jujutsukaisen.JujutsuAbilities.isTechniqueFxEnabled()); }
        );
    }

    /** Fires a single cut. Sneaking while casting makes it horizontal at waist height. */
    public static void perform(Player player, double power) {
        new WorldSlashCast(player, false).start();
        player.sendMessage(MiniMessage.miniMessage().deserialize("<dark_red>World Cutting Slash..."));
    }
}
