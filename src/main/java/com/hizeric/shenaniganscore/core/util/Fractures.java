package com.hizeric.shenaniganscore.core.util;

import org.bukkit.Location;
import org.bukkit.util.Vector;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Deterministic geometry for splitting a spherical domain into twelve shards.
 *
 * <p>This class deliberately contains no animation clock, Bukkit task, particle,
 * entity, or packet logic. It answers three separate geometric questions:</p>
 * <ol>
 *     <li><b>Ownership:</b> which shard owns a point?</li>
 *     <li><b>Seams:</b> how close is the point to a boundary between shards?</li>
 *     <li><b>Arrival:</b> at what normalized point in the shatter timeline should
 *         the fracture reach the point?</li>
 * </ol>
 *
 * <p>The shell uses the twelve actual vertices of a regular icosahedron. The
 * floor uses twelve evenly spaced radial anchors with the same stable shard IDs
 * and delay order. This is intentional: projecting icosahedron vertices onto
 * the horizontal plane creates duplicate directions, while a twelve-spoke floor
 * pattern gives every shell shard a distinct, readable continuation on the ground.</p>
 *
 * <p>All point coordinates passed to the spherical methods are offsets from the
 * domain centre, expressed in units of the domain radius. For example, a point
 * on the shell has approximately {@code x*x + y*y + z*z == 1}.</p>
 */
public final class Fractures {

    /** Number of spherical shards / fracture fronts. */
    public static final int FRONT_COUNT = 12;

    /** Portion of the full animation reserved for staggered shard starts. */
    public static final double DELAY_SPREAD = 0.24;

    /** Maximum angular distance from an icosahedron vertex inside its Voronoi cell. */
    private static final double MAX_SHARD_ANGLE = createMaxShardAngle();

    /** Largest possible best-dot minus second-best-dot value at a seed. */
    private static final double MAX_SPHERE_DOT_GAP = 1.0 - (1.0 / Math.sqrt(5.0));

    /** Largest best-dot gap at the centre of one of the twelve floor sectors. */
    private static final double MAX_FLOOR_DOT_GAP = 1.0 - Math.cos((Math.PI * 2.0) / FRONT_COUNT);

    /** Fixed, intentionally shuffled order in which the twelve fronts begin. */
    private static final int[] START_ORDER = {
            0, 7, 3, 10, 5, 1, 8, 4, 11, 2, 9, 6
    };

    /** Maps a shard ID to its position in START_ORDER. */
    private static final int[] START_RANK = createStartRanks();

    /** Unit directions of the twelve regular-icosahedron vertices. */
    private static final double[][] SEEDS = createSeeds();

    /** Unit directions of the twelve evenly spaced floor anchors. */
    private static final double[][] FLOOR_SEEDS = createFloorSeeds();

    private Fractures() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // Spherical ownership and seams
    // -------------------------------------------------------------------------

    /**
     * Returns the ID of the shard that owns a normalized spherical point.
     *
     * <p>Ownership is the seed with the greatest dot product against the point's
     * unit direction. Ties are resolved by the lower stable shard ID.</p>
     *
     * @param px x offset from the domain centre, in radii
     * @param py y offset from the domain centre, in radii
     * @param pz z offset from the domain centre, in radii
     * @return a value in {@code [0, FRONT_COUNT)}
     * @throws IllegalArgumentException if any coordinate is non-finite or the
     *                                  direction is zero-length
     */
    public static int owner(double px, double py, double pz) {
        return sample(px, py, pz).owner();
    }

    /**
     * Returns how close a spherical point is to a shard seam, in {@code [0, 1]}.
     *
     * <p>{@code 1.0} is exactly on a Voronoi boundary (the best and second-best
     * seed dots are equal); {@code 0.0} is at a shard seed. Values between them
     * form a continuous seam-strength signal that callers can threshold or use
     * to vary crack width and brightness.</p>
     *
     * <p>This describes the geometric seam network, not whether the crack has
     * appeared yet. Use {@link #arrivalFraction(double, double, double)} for
     * timing.</p>
     */
    public static double seamProximity(double px, double py, double pz) {
        return sample(px, py, pz).seamProximity();
    }

    /**
     * Returns when a spherical point is reached by its shard's fracture front.
     *
     * <p>The result is normalized to {@code [0, 1]}; it is not elapsed time.
     * The owning seed determines the staggered start. Angular distance from that
     * seed determines how far the front has to travel across its shard. The
     * farthest point in an icosahedral Voronoi cell maps to the end of the travel
     * portion, so the output remains bounded and comparable across all shards.</p>
     *
     * <p>Typical animation usage:</p>
     * <pre>{@code
     * double arrival = Fractures.arrivalFraction(nx, ny, nz);
     * boolean reached = elapsedFraction >= arrival;
     * }</pre>
     */
    public static double arrivalFraction(double px, double py, double pz) {
        return sample(px, py, pz).arrivalFraction();
    }

    /**
     * A single point's complete spherical fracture state.
     *
     * <p>{@link #owner}, {@link #seamProximity} and {@link #arrivalFraction}
     * each need the same twelve dot products. An animation that tests many
     * points should call {@link #sample(double, double, double)} once instead
     * of all three methods, so the direction is normalized once and the seeds
     * are scanned once.</p>
     *
     * @param owner            owning shard ID, in {@code [0, FRONT_COUNT)}
     * @param seamProximity    proximity to a shard seam, in {@code [0, 1]}
     * @param arrivalFraction  normalized arrival time, in {@code [0, 1]}
     */
    public record Sample(int owner, double seamProximity, double arrivalFraction) {
    }

    /**
     * Samples ownership, seam proximity and arrival in one pass.
     *
     * @throws IllegalArgumentException if any coordinate is non-finite or the
     *                                  direction is zero-length
     */
    public static Sample sample(double px, double py, double pz) {
        double length = directionLength(px, py, pz);
        double x = px / length;
        double y = py / length;
        double z = pz / length;

        int bestIndex = 0;
        double best = -Double.MAX_VALUE;
        double second = -Double.MAX_VALUE;
        for (int i = 0; i < FRONT_COUNT; i++) {
            double[] seed = SEEDS[i];
            double dot = x * seed[0] + y * seed[1] + z * seed[2];
            if (dot > best) {
                second = best;
                best = dot;
                bestIndex = i;
            } else if (dot > second) {
                second = dot;
            }
        }

        double gap = Math.max(0.0, best - second);
        double seam = 1.0 - MathUtil.clamp(gap / MAX_SPHERE_DOT_GAP, 0.0, 1.0);
        double angle = Math.acos(MathUtil.clamp(best, -1.0, 1.0));
        double travel = MathUtil.clamp(angle / MAX_SHARD_ANGLE, 0.0, 1.0);
        return new Sample(bestIndex, seam, combineArrival(bestIndex, travel));
    }

    // -------------------------------------------------------------------------
    // Floor ownership and seams
    // -------------------------------------------------------------------------

    /**
     * Returns the floor shard ID nearest to a point on the normalized floor disk.
     *
     * <p>{@code px} and {@code pz} are horizontal offsets from the domain centre,
     * each expressed in units of the domain radius. The twelve floor anchors sit
     * around the edge of the unit disk. The centre is equidistant from every
     * anchor and deterministically belongs to shard {@code 0}.</p>
     */
    public static int floorOwner(double px, double pz) {
        return floorSample(px, pz).owner();
    }

    /**
     * Returns proximity to a floor crack seam in {@code [0, 1]}.
     *
     * <p>{@code 1.0} is on a boundary between radial sectors (including the
     * central meeting point); {@code 0.0} is on a floor anchor's radial centreline.
     * This is a geometric signal only, not an animation clock.</p>
     */
    public static double floorSeamProximity(double px, double pz) {
        return floorSample(px, pz).seamProximity();
    }

    /**
     * Returns when a floor point is reached by its radial fracture front.
     *
     * <p>The floor uses Euclidean distance to the owning shard's perimeter anchor,
     * rather than spherical angular distance. Coordinates must be normalized to
     * the domain radius. A point at the exact centre is the shared final junction
     * and therefore returns {@code 1.0}.</p>
     */
    public static double floorArrivalFraction(double px, double pz) {
        return floorSample(px, pz).arrivalFraction();
    }

    /**
     * A single point's complete floor fracture state.
     *
     * <p>{@link #floorOwner}, {@link #floorSeamProximity} and
     * {@link #floorArrivalFraction} all need the same anchor scan. A floor
     * animation that tests many points should call {@link #floorSample(double, double)}
     * once instead of all three methods.</p>
     *
     * @param owner            owning floor shard ID, in {@code [0, FRONT_COUNT)}
     * @param seamProximity    proximity to a radial seam, in {@code [0, 1]}
     * @param arrivalFraction  normalized arrival time, in {@code [0, 1]}
     */
    public record FloorSample(int owner, double seamProximity, double arrivalFraction) {
    }

    /**
     * Samples floor ownership, seam proximity and arrival in one pass.
     *
     * <p>The floor centre is the shared final junction: it returns shard {@code 0},
     * seam {@code 1.0} and arrival {@code 1.0}.</p>
     *
     * @throws IllegalArgumentException if either coordinate is non-finite
     */
    public static FloorSample floorSample(double px, double pz) {
        requireFinite(px, "px");
        requireFinite(pz, "pz");

        double radialDistance = Math.hypot(px, pz);
        if (radialDistance <= 1.0E-12) {
            return new FloorSample(0, 1.0, 1.0);
        }

        int bestIndex = 0;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (int i = 0; i < FRONT_COUNT; i++) {
            double dx = px - FLOOR_SEEDS[i][0];
            double dz = pz - FLOOR_SEEDS[i][1];
            double distanceSquared = dx * dx + dz * dz;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                bestIndex = i;
            }
        }

        double x = px / radialDistance;
        double z = pz / radialDistance;
        double best = -Double.MAX_VALUE;
        double second = -Double.MAX_VALUE;
        for (double[] seed : FLOOR_SEEDS) {
            double dot = x * seed[0] + z * seed[1];
            if (dot > best) {
                second = best;
                best = dot;
            } else if (dot > second) {
                second = dot;
            }
        }

        double gap = Math.max(0.0, best - second);
        double seam = 1.0 - MathUtil.clamp(gap / MAX_FLOOR_DOT_GAP, 0.0, 1.0);

        double dx = px - FLOOR_SEEDS[bestIndex][0];
        double dz = pz - FLOOR_SEEDS[bestIndex][1];
        double travel = MathUtil.clamp(Math.hypot(dx, dz), 0.0, 1.0);
        return new FloorSample(bestIndex, seam, combineArrival(bestIndex, travel));
    }

    // -------------------------------------------------------------------------
    // Stable shard data and world-space origins
    // -------------------------------------------------------------------------

    /** Returns the normalized stagger delay for a shard, in {@code [0, DELAY_SPREAD]}. */
    public static double delayFraction(int index) {
        int shard = wrapIndex(index);
        return (START_RANK[shard] / (double) (FRONT_COUNT - 1)) * DELAY_SPREAD;
    }

    /** Returns the zero-based rank at which this shard begins to break. */
    public static int breakOrder(int index) {
        return START_RANK[wrapIndex(index)];
    }

    /** Returns a new Bukkit vector containing the unit direction of a spherical shard. */
    public static Vector direction(int index) {
        double[] seed = SEEDS[wrapIndex(index)];
        return new Vector(seed[0], seed[1], seed[2]);
    }

    /**
     * Returns a deterministic unit tangent perpendicular to a shard's direction.
     * Useful as a stable rotation axis when animating the shard's separation.
     */
    public static Vector tangentDirection(int index) {
        double[] seed = SEEDS[wrapIndex(index)];
        double ax = Math.abs(seed[0]);
        double ay = Math.abs(seed[1]);
        double az = Math.abs(seed[2]);

        // Cross with the least-aligned cardinal axis for a well-conditioned result.
        double rx;
        double ry;
        double rz;
        if (ax <= ay && ax <= az) {
            rx = 1.0;
            ry = 0.0;
            rz = 0.0;
        } else if (ay <= az) {
            rx = 0.0;
            ry = 1.0;
            rz = 0.0;
        } else {
            rx = 0.0;
            ry = 0.0;
            rz = 1.0;
        }

        double tx = seed[1] * rz - seed[2] * ry;
        double ty = seed[2] * rx - seed[0] * rz;
        double tz = seed[0] * ry - seed[1] * rx;
        double inverseLength = 1.0 / Math.sqrt(tx * tx + ty * ty + tz * tz);
        return new Vector(tx * inverseLength, ty * inverseLength, tz * inverseLength);
    }

    /** Alternates the preferred spin direction so neighbouring shards do not all twist alike. */
    public static int rotationSign(int index) {
        return (wrapIndex(index) & 1) == 0 ? 1 : -1;
    }

    /** The world-space position of one spherical fracture origin. */
    public static Location origin(Location center, double radius, int index) {
        requireCenter(center);
        requireRadius(radius);
        double[] seed = SEEDS[wrapIndex(index)];
        return center.clone().add(seed[0] * radius, seed[1] * radius, seed[2] * radius);
    }

    /** The world-space position of one matching floor fracture origin. */
    public static Location floorOrigin(Location center, double radius, int index) {
        requireCenter(center);
        requireRadius(radius);
        double[] seed = FLOOR_SEEDS[wrapIndex(index)];
        return center.clone().add(seed[0] * radius, 0.0, seed[1] * radius);
    }

    /** Returns all twelve spherical origins in stable shard-ID order. */
    public static Location[] origins(Location center, double radius) {
        requireCenter(center);
        requireRadius(radius);
        Location[] result = new Location[FRONT_COUNT];
        for (int i = 0; i < FRONT_COUNT; i++) {
            result[i] = origin(center, radius, i);
        }
        return result;
    }

    /** Returns all twelve matching floor origins in stable shard-ID order. */
    public static Location[] floorOrigins(Location center, double radius) {
        requireCenter(center);
        requireRadius(radius);
        Location[] result = new Location[FRONT_COUNT];
        for (int i = 0; i < FRONT_COUNT; i++) {
            result[i] = floorOrigin(center, radius, i);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Internal deterministic geometry
    // -------------------------------------------------------------------------

    private static double combineArrival(int shard, double travelFraction) {
        double delay = delayFraction(shard);
        return delay + (1.0 - delay) * MathUtil.clamp(travelFraction, 0.0, 1.0);
    }

    private static double[][] createSeeds() {
        double phi = (1.0 + Math.sqrt(5.0)) / 2.0;
        double[][] seeds = {
                { 0.0,  1.0,  phi}, { 0.0,  1.0, -phi},
                { 0.0, -1.0,  phi}, { 0.0, -1.0, -phi},
                { 1.0,  phi, 0.0}, {-1.0,  phi, 0.0},
                { 1.0, -phi, 0.0}, {-1.0, -phi, 0.0},
                { phi, 0.0,  1.0}, { phi, 0.0, -1.0},
                {-phi, 0.0,  1.0}, {-phi, 0.0, -1.0}
        };

        for (double[] seed : seeds) {
            double inverseLength = 1.0 / Math.sqrt(
                    seed[0] * seed[0] + seed[1] * seed[1] + seed[2] * seed[2]
            );
            seed[0] *= inverseLength;
            seed[1] *= inverseLength;
            seed[2] *= inverseLength;
        }
        return seeds;
    }

    private static double[][] createFloorSeeds() {
        double[][] seeds = new double[FRONT_COUNT][2];
        for (int i = 0; i < FRONT_COUNT; i++) {
            double angle = (Math.PI * 2.0 * i) / FRONT_COUNT;
            seeds[i][0] = Math.cos(angle);
            seeds[i][1] = Math.sin(angle);
        }
        return seeds;
    }

    private static int[] createStartRanks() {
        int[] ranks = new int[FRONT_COUNT];
        for (int rank = 0; rank < START_ORDER.length; rank++) {
            ranks[START_ORDER[rank]] = rank;
        }
        return ranks;
    }

    private static double createMaxShardAngle() {
        // A Voronoi-cell corner lies at the normalized centre of an icosahedron face.
        double adjacentDot = 1.0 / Math.sqrt(5.0);
        double faceCentreDot = (1.0 + 2.0 * adjacentDot)
                / Math.sqrt(3.0 + 6.0 * adjacentDot);
        return Math.acos(MathUtil.clamp(faceCentreDot, -1.0, 1.0));
    }

    private static double directionLength(double x, double y, double z) {
        requireFinite(x, "px");
        requireFinite(y, "py");
        requireFinite(z, "pz");
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length <= 1.0E-12) {
            throw new IllegalArgumentException("Spherical direction must not be zero-length");
        }
        return length;
    }

    private static int wrapIndex(int index) {
        return Math.floorMod(index, FRONT_COUNT);
    }

    private static void requireCenter(Location center) {
        if (center == null) {
            throw new IllegalArgumentException("center must not be null");
        }
    }

    private static void requireRadius(double radius) {
        if (!Double.isFinite(radius) || radius < 0.0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
