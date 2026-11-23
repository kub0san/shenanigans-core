package com.hizeric.shenaniganscore.battleground.player;

import com.hizeric.shenaniganscore.battleground.ability.AbilityIdMigration;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword.DashSwordId;
import com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword.ExecutionerSwordId;
import com.hizeric.shenaniganscore.core.ability.AbilityId;

public final class LegacyOwnership {

    private LegacyOwnership() { }

    public static String toCatalogEntry(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            AbilityId id = AbilityId.of(raw.trim());
            if ("dash_sword".equals(id.value()) || "dashsword".equals(id.value()))
                return DashSwordId.NEW_ID;
            if ("executioner_sword".equals(id.value()) || "executioners_sword".equals(id.value())
                    || "executionsword".equals(id.value()))
                return ExecutionerSwordId.NEW_ID;
            return AbilityId.of(AbilityIdMigration.apply(raw.trim())).value();
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}