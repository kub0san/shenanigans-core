package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.blue;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;

/**
 * Catalogue definition for Gojo's <b>Cursed Technique Lapse: Blue</b> (蒼).
 *
 * <p>All gating lives in {@code core.ability.AbilityCaster}. This class supplies the catalogue
 * definition and performs the technique once the caster approved it. The technique itself lives
 * in {@link BlueProjectile} (timing, pull physics, damage) and {@link BlueBuilder} (every visual).</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:blue");
    public static final int PRICE = 60;
    public static final int DEFAULT_COOLDOWN_SECONDS = 30;

    /** Fixed power of the standard cast. */
    public static final double DEFAULT_POWER = 1.0;

    /** Read at cast time so a config reload takes effect without re-registering. */
    public static int getCooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(TECHNIQUES, "blue", DEFAULT_COOLDOWN_SECONDS);
    }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<blue>Cursed Technique Lapse: Blue"),
                Material.END_ROD,
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

    /** The approved cast itself: no gating, no energy, no cooldown bookkeeping here. */
    public static void perform(Player player, double power) {
        new BlueProjectile(player, power).start();
        player.sendMessage(MiniMessage.miniMessage().deserialize("<blue>Cursed Technique Lapse..."));
    }
}
