package com.hizeric.shenaniganscore.battleground.deploy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure drop-site selection. Instead of spreading players out, candidates are scored on how well they
 * sit inside an engagement band (sweet spot) around the other deployed players, minus penalties for
 * terrain roughness and for landing in a mercy radius around an avoider (last killer). Tiers relax
 * flat/obstruction limits and the minimum attraction but never fully abandon attraction until the
 * last-resort tier. Bukkit types stay behind {@link DropProbe}, so every branch is unit-testable.
 *
 * <p>The arena rectangle is sampled in two passes: a coarse stride locates promising regions, then
 * the cells skipped by that stride are refined around the best coarse candidates. Both passes share
 * one heightmap cache. If the coarse pass finds nothing at all, the full-stride scan runs as a
 * fallback so a tiny clearing can never be missed by the stride alone.
 */
public final class DropZone {

    /** No standable surface (open void, or a liquid surface). */
    public static final int NO_GROUND = Integer.MIN_VALUE;
    /** Flatness assigned when a neighbour is liquid — treated as the worst possible cliff. */
    private static final int BAD_FLAT = 64;
    /** How many top coarse candidates get a full-stride refinement. */
    private static final int REFINE_TOP_K = 24;

    /** A scored landing column. {@code spawnY} is derived from the surface + obstacle clearance. */
    public record Candidate(int x, int groundY, int z, int spawnY,
                            int flatDeviation, int obstructions, double attraction, long score) { }

    /** A horizontal position, used for both anchors and avoiders. */
    public record Point(int x, int z) { }

    /** Why a selection failed, so the UI can give a specific reason instead of a generic message. */
    public enum Reason { NONE, NO_SOLID_GROUND, ALL_LIQUID, OBSTRUCTED }

    /** The outcome of a search: either a candidate (and the tier it qualified at) or a reason. */
    public record Selection(Candidate candidate, Reason reason, int tier) {
        public boolean found() { return candidate != null; }

        public static Selection of(Candidate candidate, int tier) {
            return new Selection(candidate, Reason.NONE, tier);
        }

        public static Selection failure(Reason reason) {
            return new Selection(null, reason, -1);
        }
    }

    /**
     * One relaxation step. A candidate qualifies when its flatness and obstruction counts are at
     * most {@code maxFlatDeviation} / {@code maxObstructions} and its attraction is at least
     * {@code minAttraction}. Later tiers are looser in every dimension.
     */
    public record Tier(int maxFlatDeviation, int maxObstructions, double minAttraction) { }

    /**
     * Tuning. The scan covers the whole arena rectangle; {@code sampleStep} is the fine grid stride
     * (the coarse stride is twice that). {@code sweetSpot}/{@code sigma}/{@code minGap} define the
     * engagement band. {@code regionMinY}/{@code regionMaxY} bound landing and spawn positions to
     * the arena volume, so enclosed/ceilinged arenas resolve to their interior floor.
     */
    public record Config(int sampleStep, int minY, int scanTopY, int dropHeight,
                         int spawnClearance, int clearanceHeight,
                         int regionMinY, int regionMaxY,
                         int sweetSpot, int sigma, int minGap,
                         List<Tier> tiers) {
        public Config {
            sampleStep = Math.max(1, sampleStep);
            dropHeight = Math.max(1, dropHeight);
            spawnClearance = Math.max(1, spawnClearance);
            clearanceHeight = Math.max(1, clearanceHeight);
            sweetSpot = Math.max(1, sweetSpot);
            sigma = Math.max(1, sigma);
            minGap = Math.max(1, minGap);
            if (regionMaxY < regionMinY) regionMaxY = regionMinY;
            tiers = tiers == null || tiers.isEmpty()
                    ? List.of(new Tier(Integer.MAX_VALUE, Integer.MAX_VALUE, -1e9))
                    : List.copyOf(tiers);
        }
    }

    private DropZone() { }

    public static Selection select(DropProbe probe, int minX, int maxX, int minZ, int maxZ,
                                   DropCohort cohort, Config config) {
        return select(probe, minX, maxX, minZ, maxZ, cohort, config, ThreadLocalRandom.current());
    }

    /** Testable overload with an explicit random source for deterministic tie-breaking. */
    public static Selection select(DropProbe probe, int minX, int maxX, int minZ, int maxZ,
                                   DropCohort cohort, Config config, Random random) {
        if (random == null) random = ThreadLocalRandom.current();
        if (probe == null || config == null || minX > maxX || minZ > maxZ) {
            return Selection.failure(Reason.NO_SOLID_GROUND);
        }
        DropCohort coh = cohort == null ? DropCohort.empty() : cohort;

        Map<Long, Surface> surfaces = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        int[] counts = new int[2]; // [solid, liquid]
        Set<Long> sampled = new HashSet<>();
        int step = config.sampleStep();
        int coarseStep = step * 2;

        // Pass 1 — coarse sweep. Locates the promising regions cheaply on very large arenas.
        for (int x = minX; x <= maxX; x += coarseStep) {
            for (int z = minZ; z <= maxZ; z += coarseStep) {
                if (sampled.add(key(x, z))) {
                    sample(probe, x, z, coh, config, surfaces, candidates, counts);
                }
            }
        }

        // Pass 2 — refine the cells the coarse stride skipped, around the best coarse candidates.
        List<Candidate> coarse = new ArrayList<>(candidates);
        coarse.sort(Comparator.comparingLong(Candidate::score).reversed());
        int keep = Math.min(REFINE_TOP_K, coarse.size());
        for (int i = 0; i < keep; i++) {
            Candidate centre = coarse.get(i);
            for (int dx = -step; dx <= step; dx += step) {
                for (int dz = -step; dz <= step; dz += step) {
                    int x = centre.x() + dx;
                    int z = centre.z() + dz;
                    if (x < minX || x > maxX || z < minZ || z > maxZ) continue;
                    if (sampled.add(key(x, z))) {
                        sample(probe, x, z, coh, config, surfaces, candidates, counts);
                    }
                }
            }
        }

        // A tiny clearing can fall entirely between coarse samples. Never fail because of stride.
        if (candidates.isEmpty()) {
            for (int x = minX; x <= maxX; x += step) {
                for (int z = minZ; z <= maxZ; z += step) {
                    if (sampled.add(key(x, z))) {
                        sample(probe, x, z, coh, config, surfaces, candidates, counts);
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return Selection.failure(counts[1] > 0 && counts[0] == 0
                    ? Reason.ALL_LIQUID : Reason.NO_SOLID_GROUND);
        }

        for (int tierIndex = 0; tierIndex < config.tiers().size(); tierIndex++) {
            Tier tier = config.tiers().get(tierIndex);
            Candidate best = null;
            int tied = 0;
            for (Candidate candidate : candidates) {
                if (candidate.flatDeviation() > tier.maxFlatDeviation()) continue;
                if (candidate.obstructions() > tier.maxObstructions()) continue;
                if (candidate.attraction() < tier.minAttraction()) continue;
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                    tied = 1;
                } else if (candidate.score() == best.score()) {
                    tied++;
                    if (random.nextInt(tied) == 0) best = candidate;
                }
            }
            if (best != null) return Selection.of(best, tierIndex);
        }
        return Selection.failure(Reason.OBSTRUCTED);
    }

    /** Score of a column (higher is better): flat ground, clear air and attraction all help. */
    public static long score(int flatDeviation, int obstructions, double attraction) {
        return -2L * flatDeviation - 3L * obstructions + Math.round(60.0 * attraction);
    }

    /** Samples one column and appends a candidate when it is stable and has usable headroom. */
    private static void sample(DropProbe probe, int x, int z, DropCohort cohort, Config config,
                               Map<Long, Surface> surfaces, List<Candidate> candidates, int[] counts) {
        Surface surface = surface(probe, x, z, config, surfaces);
        if (surface.y() == NO_GROUND || surface.liquid()) {
            if (surface.liquid()) counts[1]++;
            return;
        }
        counts[0]++;
        if (!stable(probe, x, z, surface.y(), config)) return;
        int flat = flatDeviation(probe, x, z, surface.y(), config, surfaces);
        int obs = obstructions(probe, x, z, surface.y(), config);
        int spawnY = spawnY(probe, x, z, surface.y(), config);
        if (spawnY == NO_GROUND) return;
        double attraction = attraction(x, z, cohort, config);
        candidates.add(new Candidate(x, surface.y(), z, spawnY, flat, obs, attraction,
                score(flat, obs, attraction)));
    }

    /**
     * How much we want to land at (x,z): the peak of a Gaussian around the nearest anchor, minus a
     * mercy penalty inside twice the min-gap radius of an avoider (the player's last killer).
     */
    private static double attraction(int x, int z, DropCohort cohort, Config config) {
        double best = -Double.MAX_VALUE;
        for (DropCohort.Anchor a : cohort.attractors()) {
            double dx = x - a.x();
            double dz = z - a.z();
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < config.minGap()) {
                best = Math.max(best, -2.0);
                continue;
            }
            double n = (d - config.sweetSpot()) / (double) config.sigma();
            best = Math.max(best, a.weight() * Math.exp(-0.5 * n * n));
        }
        double result = best == -Double.MAX_VALUE ? 0.0 : best;
        for (DropZone.Point p : cohort.avoiders()) {
            double dx = x - p.x();
            double dz = z - p.z();
            if (Math.sqrt(dx * dx + dz * dz) < config.minGap() * 2.0) result -= 1.5;
        }
        return result;
    }

    /** Reject pillars and floating platforms: need support below (or the arena floor plane) and a lateral neighbour. */
    private static boolean stable(DropProbe probe, int x, int z, int y, Config config) {
        if (!probe.isSolid(x, y - 1, z) && !isThinFloorColumn(probe, x, z, y, config.regionMinY())) return false;
        return hasLateralSupport(probe, x, z, y);
    }

    /** A solid surface sitting exactly on the arena floor plane with no block supporting it from below. */
    public static boolean isThinFloorColumn(DropProbe probe, int x, int z, int y, int regionMinY) {
        return y == regionMinY && probe.isSolid(x, y, z) && !probe.isSolid(x, y - 1, z);
    }

    private static boolean hasLateralSupport(DropProbe probe, int x, int z, int y) {
        return probe.isSolid(x - 1, y, z) || probe.isSolid(x + 1, y, z)
                || probe.isSolid(x, y, z - 1) || probe.isSolid(x, y, z + 1);
    }

    /** True when any coarse-sampled column of the arena has a single-layer floor. */
    public static boolean hasThinFloor(DropProbe probe, int minX, int maxX, int minZ, int maxZ,
                                       int floorY, int step) {
        step = Math.max(1, step);
        for (int x = minX; x <= maxX; x += step) {
            for (int z = minZ; z <= maxZ; z += step) {
                if (isThinFloorColumn(probe, x, z, floorY, floorY)
                        && hasLateralSupport(probe, x, z, floorY)) return true;
            }
        }
        return false;
    }

    /**
     * Release Y is capped by the heightmap hint (first non-air from above) so we never spawn inside
     * a leaf/tree block or against a roof. The cap applies only when that hint is strictly above the
     * landing surface — on flat ground the hint IS the surface, and capping to {@code hint - 1}
     * would put the release below the floor and reject every column.
     */
    private static int spawnY(DropProbe probe, int x, int z, int groundY, Config config) {
        int hint = probe.surfaceY(x, z);
        int candidate = Math.max(groundY + config.dropHeight(),
                highestObstruction(probe, x, z, groundY, config) + config.spawnClearance());
        if (hint != Integer.MIN_VALUE && hint > groundY) {
            candidate = Math.min(candidate, Math.min(hint, config.scanTopY()) - 1);
        }
        candidate = Math.min(candidate, config.regionMaxY() - 2);
        while (candidate > groundY + 1
                && (candidate + 1 > config.regionMaxY() - 1
                || !probe.isAirLike(x, candidate, z)
                || !probe.isAirLike(x, candidate + 1, z))) {
            candidate--;
        }
        if (candidate <= groundY + 1) return NO_GROUND;
        if (candidate + 1 > config.regionMaxY() - 1) return NO_GROUND;
        if (!probe.isAirLike(x, candidate, z)
                || !probe.isAirLike(x, candidate + 1, z)) return NO_GROUND;
        return candidate;
    }

    /** Highest non-air block in the drop column band, used to lift the spawn above obstacles. */
    private static int highestObstruction(DropProbe probe, int x, int z, int groundY, Config config) {
        int highest = groundY;
        int top = Math.min(groundY + Math.max(config.clearanceHeight(),
                config.dropHeight() + config.spawnClearance()), config.regionMaxY() - 1);
        for (int y = groundY + 1; y <= top; y++) {
            if (!probe.isAirLike(x, y, z)) highest = y;
        }
        return highest;
    }

    /** Counts obstructing blocks in a small plus-shaped disc from the surface up the clear band. */
    private static int obstructions(DropProbe probe, int x, int z, int groundY, Config config) {
        int count = 0;
        int[][] offsets = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        int top = Math.min(groundY + Math.max(config.clearanceHeight(),
                config.dropHeight() + config.spawnClearance()), config.regionMaxY() - 1);
        for (int y = groundY + 1; y <= top; y++) {
            for (int[] offset : offsets) {
                if (!probe.isAirLike(x + offset[0], y, z + offset[1])) count++;
            }
        }
        return count;
    }

    /** Worst surface-height difference from the centre over rings 2 and 4; liquid is worst. */
    private static int flatDeviation(DropProbe probe, int x, int z, int groundY, Config config,
                                     Map<Long, Surface> surfaces) {
        int worst = 0;
        for (int reach : new int[] {2, 4}) {
            int[][] neighbours = {{x, z - reach}, {x, z + reach},
                    {x + reach, z}, {x - reach, z}};
            for (int[] neighbour : neighbours) {
                Surface s = surface(probe, neighbour[0], neighbour[1], config, surfaces);
                if (s.liquid()) return BAD_FLAT;
                if (s.y() == NO_GROUND) continue;
                worst = Math.max(worst, Math.abs(s.y() - groundY));
            }
        }
        return worst;
    }

    /**
     * First viable non-air block at or below the probe's surface hint (or the scan top). Blocks too
     * close to the arena roof, or outside the arena Y bounds, are skipped so enclosed box arenas
     * resolve to their interior floor instead of the ceiling top.
     */
    private static Surface surface(DropProbe probe, int x, int z, Config config,
                                   Map<Long, Surface> surfaces) {
        long key = key(x, z);
        Surface cached = surfaces.get(key);
        if (cached != null) return cached;
        int hint = probe.surfaceY(x, z);
        int startY = hint == Integer.MIN_VALUE ? config.scanTopY() : Math.min(hint, config.scanTopY());
        startY = Math.min(startY, config.regionMaxY() - 1);
        Surface found = new Surface(NO_GROUND, false);
        for (int y = startY; y >= config.minY(); y--) {
            if (!probe.isAirLike(x, y, z)) {
                boolean liquid = probe.isLiquid(x, y, z);
                if (y < config.regionMinY() || y > config.regionMaxY() - 2) continue;
                found = new Surface(y, liquid);
                break;
            }
        }
        surfaces.put(key, found);
        return found;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffff_ffffL);
    }

    private record Surface(int y, boolean liquid) { }
}
