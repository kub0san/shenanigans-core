package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.ability.AbilityDefinition;
import com.hizeric.shenaniganscore.core.ability.AbilityId;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;

/**
 * The dash sword's cast: a high-momentum launch with a display-entity shockwave and a ghost-sword
 * trail.
 *
 * <p>The ability is never sold on its own. Its catalog row is the item row, so owning the sword is
 * what unlocks the cast, and the held item is what gates it.</p>
 */
public final class DashSwordAbility {

    public static final AbilityId ID = DashSwordItem.ABILITY_ID;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private DashSwordAbility() { }

    public static AbilityDefinition definition() {
        return new AbilityDefinition(
                ID,
                MM.deserialize("<aqua><bold>Dash"),
                Material.NETHERITE_SWORD,
                0,
                DashSwordItem::cooldownSeconds,
                1.0,
                (player, power) -> DashSwordEffect.launch(player),
                player -> ShenanigansCore.getInstance().getSessionManager()
                        .hasActiveSession(player.getUniqueId()),
                false
        );
    }
}
