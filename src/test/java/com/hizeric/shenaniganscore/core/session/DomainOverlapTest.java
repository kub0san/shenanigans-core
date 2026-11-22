package com.hizeric.shenaniganscore.core.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The overlap decision behind {@link SessionManager#isOverlapping}.
 *
 * <p>A new domain may not expand where it would touch a <em>live</em> domain:
 * spheres in the same world whose centers are closer than the sum of their
 * radii. The historical bug: the loop never checked {@code session.isActive()},
 * so a session that was closing — or one that ended abnormally and lingered in
 * the map — kept blocking every new domain in range forever. The fix is to skip
 * inactive sessions, exactly as the sibling {@code isInsideAnyDomain} already
 * does. This is the pure decision; the {@link org.bukkit.Location} plumbing is
 * exercised in-game.</p>
 */
class DomainOverlapTest {

    // World identity is reference equality (mirrors Location.getWorld() != ...).
    private static final Object WORLD_A = new Object();
    private static final Object WORLD_B = new Object();

    private static DomainOverlap.Footprint live(Object world, double x, double y, double z, double radius) {
        return new DomainOverlap.Footprint(world, x, y, z, radius, true);
    }

    private static DomainOverlap.Footprint dead(Object world, double x, double y, double z, double radius) {
        return new DomainOverlap.Footprint(world, x, y, z, radius, false);
    }

    @Test
    void aLiveOverlappingDomainBlocks() {
        // Candidate at origin, radius 10; another live domain centered 15 away, radius 10 → 15 < 20.
        assertTrue(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(live(WORLD_A, 15, 0, 0, 10))),
                "a live domain whose sphere touches the candidate blocks it");
    }

    @Test
    void anInactiveOverlappingDomainDoesNotBlock() {
        // The bug: an ending/ended session at the same spot must not block.
        assertFalse(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(dead(WORLD_A, 15, 0, 0, 10))),
                "an inactive session must not block a new overlapping domain");
    }

    @Test
    void anOverlappingDomainInAnotherWorldDoesNotBlock() {
        assertFalse(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(live(WORLD_B, 0, 0, 0, 10))),
                "domains in different worlds never overlap");
    }

    @Test
    void aLiveDomainFarEnoughAwayDoesNotBlock() {
        // Centers 25 apart, radii 10 + 10 = 20 → 25 > 20, clear.
        assertFalse(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(live(WORLD_A, 25, 0, 0, 10))),
                "a live domain whose sphere does not reach the candidate is clear");
    }

    @Test
    void aTouchingBoundaryIsClear() {
        // Exactly at the sum of radii (20): the test is strict (<), so touching is allowed.
        assertFalse(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(live(WORLD_A, 20, 0, 0, 10))),
                "spheres that merely touch do not overlap");
    }

    @Test
    void oneDeadAndOneClearLiveStillDoesNotBlock() {
        assertFalse(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(dead(WORLD_A, 5, 0, 0, 10), live(WORLD_A, 100, 0, 0, 10))),
                "a dead overlap plus a distant live domain leaves the candidate clear");
    }

    @Test
    void oneDeadAndOneLiveOverlapBlocks() {
        assertTrue(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(dead(WORLD_A, 5, 0, 0, 10), live(WORLD_A, 8, 0, 0, 10))),
                "a single live overlapping domain still blocks, regardless of a dead one");
    }

    @Test
    void theVerticalAxisCountsTowardDistance() {
        // Same X/Z but 15 apart on Y, radii sum 20 → 3D distance 15 < 20, blocks.
        assertTrue(DomainOverlap.overlaps(WORLD_A, 0, 0, 0, 10,
                List.of(live(WORLD_A, 0, 15, 0, 10))),
                "the sphere test is three-dimensional, matching Location.distanceSquared");
    }
}