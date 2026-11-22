package com.hizeric.shenaniganscore.core.session;

/**
 * How a domain should treat a player caught below the floor plane.
 *
 * <p>At capture time every captive is classified against the domain plane
 * ({@link DomainSession#getPlaneY()}):</p>
 * <ul>
 *     <li>{@link #RISE} — build an animated elevator platform under their feet and
 *     lift them smoothly to the floor plane, merging into the domain floor at
 *     arrival. Used for clear vertical paths.</li>
 *     <li>{@link #GLIDE} — a guided particle-corridor glide up into the domain
 *     interior, used when a straight rise is obstructed (caves). Never pushes
 *     through solid blocks, never suffocates.</li>
 * </ul>
 */
public enum UnderLevelMode {

    /** Animated elevator rise — the default for a clear vertical path. */
    RISE,

    /** Guided glide — used when the rise corridor is obstructed. */
    GLIDE
}