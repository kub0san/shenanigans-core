package com.hizeric.shenaniganscore.battleground.player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Remembers who a staging hide actually affected, so exit only un-hides those viewers. */
public final class HiddenViewers {

    private final Map<UUID, Set<UUID>> viewers = new ConcurrentHashMap<>();

    public void add(UUID staged, UUID viewer) {
        if (staged == null || viewer == null) return;
        viewers.computeIfAbsent(staged, ignored -> ConcurrentHashMap.newKeySet()).add(viewer);
    }

    /** @return the viewers to restore, and forgets them so a second exit cannot un-hide anyone. */
    public Set<UUID> drain(UUID staged) {
        if (staged == null) return Set.of();
        Set<UUID> recorded = viewers.remove(staged);
        return recorded == null ? Set.of() : Set.copyOf(recorded);
    }

    public void forget(UUID staged) {
        if (staged != null) viewers.remove(staged);
    }
}
