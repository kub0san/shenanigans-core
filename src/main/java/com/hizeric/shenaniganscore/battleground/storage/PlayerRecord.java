package com.hizeric.shenaniganscore.battleground.storage;

import java.util.List;
import java.util.UUID;

/** A plain, server-free snapshot of every persistent field of a {@code BattlegroundPlayer}. */
public record PlayerRecord(
        UUID uuid,
        int credits,
        int kills,
        int deaths,
        int streak,
        int bestStreak,
        List<String> owned,
        List<String> rail,
        String layout
) {
    public PlayerRecord {
        owned = owned == null ? List.of() : List.copyOf(owned);
        rail = rail == null ? List.of() : List.copyOf(rail);
        layout = layout == null ? "" : layout;
    }
}
