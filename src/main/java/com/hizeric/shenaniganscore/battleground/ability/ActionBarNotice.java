package com.hizeric.shenaniganscore.battleground.ability;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A short-lived action-bar message that outlives the periodic HUD refresh. Without it the HUD
 * overwrites a "not ready" nudge within five ticks (250 ms).
 *
 * <p>Notices are priority-ranked: a higher priority replaces a lower one, a repeating message
 * coalesces and extends its TTL, and a sticky notice stays until explicitly cleared.</p>
 */
public final class ActionBarNotice {

    public enum Priority {
        LOW, NORMAL, HIGH, CRITICAL
    }

    private record Notice(String message, long expiresAt, Priority priority, boolean sticky) { }

    private final Map<UUID, Notice> notices = new ConcurrentHashMap<>();

    /** Legacy, normal-priority, non-sticky notice. */
    public void show(UUID uuid, String message, long now, long durationMillis) {
        show(uuid, Priority.NORMAL, message, now, durationMillis, false);
    }

    public void show(UUID uuid, Priority priority, String message, long now, long durationMillis) {
        show(uuid, priority, message, now, durationMillis, false);
    }

    public void show(UUID uuid, Priority priority, String message, long now, long durationMillis,
                     boolean sticky) {
        if (uuid == null || message == null) return;
        Priority level = priority == null ? Priority.NORMAL : priority;
        long expires = sticky ? Long.MAX_VALUE : now + durationMillis;
        notices.compute(uuid, (key, existing) -> {
            if (existing == null) return new Notice(message, expires, level, sticky);
            if (existing.message().equals(message)) {
                long extended = existing.sticky() || sticky ? Long.MAX_VALUE : expires;
                Priority kept = existing.priority().ordinal() >= level.ordinal()
                        ? existing.priority() : level;
                return new Notice(message, extended, kept, existing.sticky() || sticky);
            }
            if (existing.priority().ordinal() > level.ordinal()) return existing;
            return new Notice(message, expires, level, sticky);
        });
    }

    public boolean active(UUID uuid, long now) {
        Notice notice = uuid == null ? null : notices.get(uuid);
        return notice != null && notice.expiresAt() > now;
    }

    /** @return the message if still live, otherwise {@code null} (and drops the expired entry). */
    public String message(UUID uuid, long now) {
        Notice notice = uuid == null ? null : notices.get(uuid);
        if (notice == null) return null;
        if (notice.expiresAt() <= now) {
            notices.remove(uuid, notice);
            return null;
        }
        return notice.message();
    }

    public void clear(UUID uuid) {
        if (uuid != null) notices.remove(uuid);
    }
}
