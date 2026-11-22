package com.hizeric.shenaniganscore.core.session;

/**
 * Pure domain-overlap geometry, free of Bukkit {@link org.bukkit.Location}s.
 *
 * <p>{@link SessionManager#isOverlapping} snapshots each existing session's
 * footprint and feeds it here. The rule: a candidate domain at {@code (x,y,z)}
 * with {@code radius} overlaps a live domain whose sphere it touches (3D center
 * distance strictly less than the sum of radii) in the same world. Inactive
 * sessions are ignored entirely — an ending or stale session must never block a
 * new domain. See {@code DomainOverlapTest}.</p>
 */
final class DomainOverlap {

    /**
     * A snapshot of one existing domain relevant to the overlap test.
     *
     * @param worldKey a world identity token compared by reference (mirrors
     *                 {@code Location.getWorld() != other.getWorld()})
     * @param active   whether that domain's session is still active
     */
    record Footprint(Object worldKey, double x, double y, double z, double radius, boolean active) {
    }

    private DomainOverlap() {
    }

    /** True if the candidate sphere touches any live footprint in the same world. */
    static boolean overlaps(Object worldKey, double x, double y, double z, double radius,
                            Iterable<Footprint> others) {
        for (Footprint f : others) {
            if (!f.active()) continue;
            if (f.worldKey() != worldKey) continue;
            double dx = f.x() - x;
            double dy = f.y() - y;
            double dz = f.z() - z;
            double minDistance = f.radius() + radius;
            if (dx * dx + dy * dy + dz * dz < minDistance * minDistance) {
                return true;
            }
        }
        return false;
    }
}