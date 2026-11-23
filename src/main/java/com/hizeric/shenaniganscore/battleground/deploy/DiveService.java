package com.hizeric.shenaniganscore.battleground.deploy;

import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are currently sky-diving. While a player is diving they are locked out of
 * combat and block interaction and immune to damage; the lock is released the instant they land, so
 * there is no post-landing grace window. Backed by a concurrent set so the per-tick dive task and the
 * event listeners can read it without synchronising.
 */
public final class DiveService {

    private final Set<UUID> diving = ConcurrentHashMap.newKeySet();
    // Set by SkyDrop's constructor; lets every existing clear() caller stop the dive task too.
    private volatile SkyDrop skyDrop;

    void attach(SkyDrop skyDrop) {
        this.skyDrop = skyDrop;
    }

    public boolean isDiving(UUID uuid) {
        return uuid != null && diving.contains(uuid);
    }

    public boolean isDiving(Player player) {
        return player != null && isDiving(player.getUniqueId());
    }

    void mark(UUID uuid) {
        if (uuid != null) diving.add(uuid);
    }

    public void clear(UUID uuid) {
        if (uuid == null) return;
        diving.remove(uuid);
        SkyDrop sink = skyDrop;
        if (sink != null) sink.cancel(uuid);
    }

    public void clear() {
        diving.clear();
        SkyDrop sink = skyDrop;
        if (sink != null) sink.cancelAll();
    }
}