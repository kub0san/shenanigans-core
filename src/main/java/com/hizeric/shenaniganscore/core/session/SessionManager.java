package com.hizeric.shenaniganscore.core.session;

import com.hizeric.shenaniganscore.battleground.ability.TechniqueHud;
import com.hizeric.shenaniganscore.core.terrain.DomainFloorRestoreLedger;
import org.bukkit.Location;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every active Domain Expansion, keyed by the caster's UUID.
 */
public class SessionManager {

    private final Map<UUID, DomainSession> activeSessions = new ConcurrentHashMap<>();
    /** Stable unmodifiable view — allocated once, never per read (F-18). */
    private final Map<UUID, DomainSession> activeSessionsView =
            Collections.unmodifiableMap(activeSessions);

    /**
     * Atomically registers, captures and activates a session for its caster.
     *
     * @return {@code true} if the session started; {@code false} if the caster
     *         already owns a live domain or the footprint overlaps an existing
     *         one (F-11, F-23). Callers must not run their timeline on a
     *         rejected session.
     */
    public boolean startSession(DomainSession session) {
        if (session == null || session.getCaster() == null) return false;
        // Issue #35: hold new domains until a previous shutdown's floor blocks have been replayed,
        // so a fresh floor cannot be measured over stale originals at the same coordinates.
        if (DomainFloorRestoreLedger.isReplaying()) return false;
        UUID uuid = session.getCaster().getUniqueId();
        // Serialize admission so two concurrent casts cannot both pass the
        // per-caster / overlap checks (F-11, F-23).
        synchronized (activeSessions) {
            if (activeSessions.containsKey(uuid)) return false;
            Location center = session.getCenter();
            if (center != null && isOverlapping(center, session.getRadius())) return false;
            activeSessions.put(uuid, session);
        }
        session.capture();
        session.activate();
        // Issue #73: a domain owns the caption; no notice from the cast may linger.
        TechniqueHud.clearNotice(session.getCaster());
        return true;
    }

    /** Closes and unregisters the session owned by the given player, if any. */
    public void stopSession(UUID uuid) {
        stopSession(uuid, SessionEndReason.NORMAL);
    }

    /**
     * Ends the session owned by the given player with a specific reason, so the
     * domain can choose its outro (a collapse for an ordinary end, a shatter for
     * a caster death).
     */
    public void stopSession(UUID uuid, SessionEndReason reason) {
        if (uuid == null) return;
        DomainSession session = activeSessions.get(uuid);
        if (session != null) {
            // end() unregisters the session via removeSession(...)
            session.end(reason == null ? SessionEndReason.NORMAL : reason);
        }
    }

    /** Whether the given player currently has an active domain. */
    public boolean hasActiveSession(UUID uuid) {
        DomainSession session = activeSessions.get(uuid);
        return session != null && session.isActive();
    }

    /** True while at least one domain session is active. */
    public boolean hasActiveSessions() {
        for (DomainSession session : activeSessions.values()) {
            if (session != null && session.isActive()) return true;
        }
        return false;
    }

    /** Emergency cleanup for onDisable / plugin shutdown. */
    public void stopAll() {
        List<DomainSession> sessions = List.copyOf(activeSessions.values());
        for (DomainSession session : sessions) {
            session.closeForShutdown();
        }
        activeSessions.clear();
    }

    /** @return an unmodifiable view of all active sessions, keyed by caster UUID. */
    public Map<UUID, DomainSession> getActiveSessions() {
        return activeSessionsView;
    }

    /**
     * True if a domain of {@code radius} blocks at {@code center} would touch any
     * active domain (spheres in the same world whose centers are closer than the
     * sum of their radii). Used to block overlapping domain expansions.
     */
    public boolean isOverlapping(Location center, double radius) {
        if (center == null || center.getWorld() == null) return false;
        // Snapshot the live footprints first; an inactive session (ending, or a
        // stale one that never left the map) must not block a new domain.
        List<DomainOverlap.Footprint> others = new java.util.ArrayList<>(activeSessions.size());
        for (DomainSession session : activeSessions.values()) {
            Location other = session.getCenter();
            if (other == null || other.getWorld() == null) continue;
            others.add(new DomainOverlap.Footprint(
                    other.getWorld(), other.getX(), other.getY(), other.getZ(),
                    session.getRadius(), session.isActive()));
        }
        return DomainOverlap.overlaps(center.getWorld(),
                center.getX(), center.getY(), center.getZ(), radius, others);
    }

    /**
     * True if the given location is inside the radius of any active domain.
     *
     * <p>Used by the indicator layer: every domain owns the action bar while it is live
     * (the caster's timer, plus per-victim status bars).</p>
     *
     * <p>Same-world only — a location in another world is never inside a domain.</p>
     */
    public boolean isInsideAnyDomain(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        for (DomainSession session : activeSessions.values()) {
            if (!session.isActive()) continue;
            Location center = session.getCenter();
            if (center == null || center.getWorld() != loc.getWorld()) continue;
            double radius = session.getRadius();
            if (loc.distanceSquared(center) <= radius * radius) return true;
        }
        return false;
    }

    /** Internal removal hook used by {@link DomainSession#close()} implementations. */
    public void removeSession(DomainSession session) {
        if (session == null || session.getCaster() == null) return;
        // remove(key, value): never removes a session that has already been replaced
        activeSessions.remove(session.getCaster().getUniqueId(), session);
    }
}
