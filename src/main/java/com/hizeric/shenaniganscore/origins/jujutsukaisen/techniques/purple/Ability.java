package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques.purple;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import static com.hizeric.shenaniganscore.core.config.AbilityTuningConfig.Section.TECHNIQUES;

/**
 * Catalogue definition for Gojo's <b>Hollow Technique: Purple</b> (虚式「茈」).
 *
 * <p>Same shape as Red and Blue: this class supplies the catalogue definition and performs the
 * technique once the shared caster approved it. The technique itself lives in
 * {@link PurpleProjectile} (timing, erasure, damage) and {@link PurpleBuilder} (every visual).</p>
 *
 * <p>Blue and Red are conjured, fused in front of the user, and the imaginary mass is fired
 * forward, erasing everything in its path. Power 1.0–5.0 scales the width, range and damage;
 * from 4.0 up it is a "maximum output" Purple.</p>
 */
public class Ability {

    public static final AbilityId ID = AbilityId.of("jjk:purple");
    public static final int PRICE = 150;

    /** Cooldown after a successful cast (hardcoded). */
    public static final int DEFAULT_COOLDOWN_SECONDS = 60;

    /** Fixed power of the standard cast. */
    public static final double DEFAULT_POWER = 1.0;

    public static int getCooldownSeconds() {
        return AbilityTuningConfig.current().cooldown(TECHNIQUES, "purple", DEFAULT_COOLDOWN_SECONDS);
    }

    /** Sneaking at cast selects the existing remote SEND/BLAST path. */
    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MiniMessage.miniMessage().deserialize("<light_purple>Hollow Technique: Purple"),
                Material.PURPLE_CONCRETE,
                PRICE,
                Ability::getCooldownSeconds,
                DEFAULT_POWER,
                (player, power) -> perform(player, power, player.isSneaking()),
                player -> ShenanigansCore.getInstance().getSessionManager()
                        .hasActiveSession(player.getUniqueId()),
                false
        );
    }

    /** Standard perform: blue/red fuses, forward erasure. */
    public static void perform(Player player, double power, boolean remote) {
        new PurpleProjectile(player, power, remote).start();
        player.sendMessage(MiniMessage.miniMessage().deserialize(
                "<light_purple>Hollow Technique: Purple" + (remote ? " <gray>(remote)" : "") + "..."));
    }
}