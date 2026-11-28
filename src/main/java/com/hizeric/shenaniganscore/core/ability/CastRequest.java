package com.hizeric.shenaniganscore.core.ability;

import org.bukkit.entity.Player;

import java.util.Objects;

/** One cast attempt: the requesting player, their rail slot and how it was triggered. */
public record CastRequest(Player player, int slot, CastSource source) {

    public CastRequest {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
    }
}
