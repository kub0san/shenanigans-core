package com.hizeric.shenaniganscore.core.session;

/**
 * How a domain returns its captives when it ends.
 *
 * <p>The return mode is chosen per domain ({@link DomainSession#returnMode()})
 * and is hardcoded. {@link #R2} is the default.</p>
 *
 * <ul>
 *     <li>{@link #R1} — in-place resolve: each captive is placed back exactly
 *     where they were captured (snap-to-original). Simple, never moves anyone
 *     back into a wall.</li>
 *     <li>{@link #R2} — animated glide: each captive eases out from their
 *     current position back to their capture point (or a safe near-point),
 *     synced with the shell collapse/shatter and the floor recede. The default.</li>
 *     <li>{@link #R3} — safe-equivalent-of-original: like R1, but if the
 *     capture point is now occupied by a domain-made block (or the plane is
 *     still rising), the captive is nudged to the nearest safe equivalent
 *     (nearest air space on the real ground, above the domain plane if
 *     inside).</li>
 *     <li>{@link #R4} — two-phase handoff: position the captive out of the
 *     domain first, then resolve their final spot after the domain is fully
 *     closed, so they never see a flickering restoration.</li>
 *     <li>{@link #EXACT_REWIND} — variant: teleport every captive back to their
 *     exact capture location regardless of intervening terrain (the old
 *     behaviour). Use with care.</li>
 * </ul>
 */
public enum ReturnMode {

    /** Place the captive back exactly where they were captured. */
    R1,

    /** Animated glide back to a safe spot — the default. */
    R2,

    /** Safe equivalent of the original spot if the original is occupied. */
    R3,

    /** Move them out of the domain first, then resolve after close. */
    R4,

    /** Teleport back to the exact capture point regardless of terrain. */
    EXACT_REWIND
}