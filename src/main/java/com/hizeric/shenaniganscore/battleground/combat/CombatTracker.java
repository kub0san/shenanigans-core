package com.hizeric.shenaniganscore.battleground.combat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/** Per-victim damage contributions, used to attribute kills within a recent time window. */
public final class CombatTracker {

    /** Hits older than this can never affect a 10-second kill window; prune to bound memory. */
    private static final long MAX_HIT_AGE_MS = 10L * 60L * 1000L;

    private record Mark(double damage, long at) { }

    private final LongSupplier clock;
    private final Map<UUID, Map<UUID, Deque<Mark>>> byVictim = new ConcurrentHashMap<>();

    public CombatTracker() {
        this(System::currentTimeMillis);
    }

    /** Injectable clock so the time-window rule is unit-testable. */
    public CombatTracker(LongSupplier clock) {
        this.clock = clock;
    }

    public void record(UUID victim, UUID attacker, double damage) {
        if (victim == null || attacker == null || victim.equals(attacker) || damage <= 0) return;
        long now = clock.getAsLong();
        Map<UUID, Deque<Mark>> attackers = byVictim.computeIfAbsent(victim,
                ignored -> new ConcurrentHashMap<>());
        Deque<Mark> hits = attackers.computeIfAbsent(attacker, ignored -> new ArrayDeque<>());
        synchronized (hits) {
            hits.addLast(new Mark(damage, now));
            prune(hits, now);
        }
    }

    /** @return the attacker who dealt the most damage in the window, or {@code null}. */
    public UUID primary(UUID victim, int windowSeconds) {
        if (victim == null) return null;
        long cutoff = clock.getAsLong() - windowSeconds * 1000L;
        Map<UUID, Deque<Mark>> attackers = byVictim.get(victim);
        if (attackers == null) return null;
        UUID best = null;
        double most = 0.0;
        for (Map.Entry<UUID, Deque<Mark>> entry : attackers.entrySet()) {
            Deque<Mark> hits = entry.getValue();
            double recent;
            synchronized (hits) {
                recent = 0.0;
                for (Mark mark : hits) {
                    if (mark.at() >= cutoff) recent += mark.damage();
                }
            }
            if (recent > most) {
                most = recent;
                best = entry.getKey();
            }
        }
        return best;
    }

    public void clear(UUID victim) {
        if (victim != null) byVictim.remove(victim);
    }

    public void clearAll() {
        byVictim.clear();
    }

    private static void prune(Deque<Mark> hits, long now) {
        long oldest = now - MAX_HIT_AGE_MS;
        while (!hits.isEmpty() && hits.peekFirst().at() < oldest) hits.removeFirst();
    }
}
