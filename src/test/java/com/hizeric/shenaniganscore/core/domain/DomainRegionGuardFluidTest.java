package com.hizeric.shenaniganscore.core.domain;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the boundary of {@link DomainRegionGuard}'s fluid/ignite gate.
 *
 * <p>A domain floor claims thousands of blocks, including the air columns it cuts away above the
 * floor. A blanket "claimed => deny" rule freezes every water/lava spread that reaches one of
 * those positions, and a claim whose session is gone (teardown finished, or a leak) freezes it
 * forever because {@code isProtected} fails closed to {@code true}. The fluid/ignite gate must
 * therefore deny only for a <em>live, active</em> domain, while every other mutation handler keeps
 * the teardown fail-safe. No live server is needed — the decision is pure.</p>
 */
class DomainRegionGuardFluidTest {

    private static final UUID LIVE = UUID.randomUUID();
    private static final UUID STALE = UUID.randomUUID();

    @Test
    void fluidFlowsThroughUnclaimedSpace() {
        assertFalse(DomainRegionGuard.deniedByLiveClaim(null, () -> Set.of(LIVE)),
                "no claim means no domain is protecting the destination");
    }

    @Test
    void aStaleClaimDoesNotFreezeFluid() {
        // Owner present, session gone: this is exactly the leak that froze lava/water forever.
        assertFalse(DomainRegionGuard.deniedByLiveClaim(STALE, () -> Set.of(LIVE)),
                "a claim without a live session must not block fluid flow");
    }

    @Test
    void aLiveActiveDomainStillStopsFluid() {
        assertTrue(DomainRegionGuard.deniedByLiveClaim(LIVE, () -> Set.of(LIVE)),
                "an active domain's blocks stay protected from external fluid flow");
    }

    @Test
    void aSessionThatEndedButIsStillInTheMapDoesNotStopFluid() {
        // The live-owner set is built from isActive() sessions only; a deactivating session
        // must not keep freezing fluid while its floor is handed back.
        assertFalse(DomainRegionGuard.deniedByLiveClaim(LIVE, () -> Set.of(STALE)),
                "only active sessions count as live protection");
    }

    @Test
    void theTeardownFailSafeStillProtectsOtherMutations() {
        // isProtected() deliberately fails closed to true for a claim during teardown;
        // narrowing the gate for fluids must not weaken break/explode/burn protection.
        assertTrue(DomainRegionGuard.mutationBlocked(true, false, false),
                "claimed with no session: fail closed (teardown protection)");
        assertTrue(DomainRegionGuard.mutationBlocked(true, true, true),
                "claimed by an indestructible domain: blocked");
        assertFalse(DomainRegionGuard.mutationBlocked(true, true, false),
                "a session whose blocks are destructible does not block");
        assertFalse(DomainRegionGuard.mutationBlocked(false, false, false),
                "unclaimed blocks are always mutable");
    }
}
