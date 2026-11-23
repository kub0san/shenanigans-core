package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

/**
 * The Executioner's Sword cast: arms the next landed sword hit for the heavy uppercut or
 * the Final Verdict execution, depending on the target's health at contact.
 */
public final class ExecutionerSwordAbility {

    public static final AbilityId ID = ExecutionerSwordItem.ABILITY_ID;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ExecutionerSwordAbility() {
    }

    public static AbilityDefinition definition(ExecutionerSwordHitListener listener) {
        return new AbilityDefinition(
                ID,
                MM.deserialize("<dark_red><bold>Executioner's Sword"),
                Material.DIAMOND_SWORD,
                0,
                ExecutionerSwordItem::cooldownSeconds,
                1.0,
                (player, power) -> listener.arm(player),
                player -> ShenanigansCore.getInstance().getSessionManager()
                        .hasActiveSession(player.getUniqueId()),
                false
        );
    }
}