package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;

public final class MomentumMaceAbility {
    public static final AbilityId ID = MomentumMaceItem.ABILITY_ID;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MomentumMaceAbility() { }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MM.deserialize("<aqua><bold>Kinetic Drive"),
                Material.MACE,
                0,
                MomentumMaceItem::cooldownSeconds,
                1.0,
                (player, power) -> {
                    if (player.isSneaking()) MomentumMaceMovement.meteorDive(player);
                    else MomentumMaceMovement.vault(player);
                },
                player -> ShenanigansCore.getInstance().getSessionManager().hasActiveSession(player.getUniqueId()),
                false
        );
    }
}