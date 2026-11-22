package com.hizeric.shenaniganscore.core.session;

/**
 * Why a domain session is ending.
 *
 * <p>The reason lets a domain pick its outro without the core knowing anything
 * about the domain itself:</p>
 * <ul>
 *     <li>{@link #NORMAL} — the domain ran its course, the caster quit, or the
 *     server is shutting down. The barrier collapses in reverse.</li>
 *     <li>{@link #CASTER_DEATH} — the caster died inside their own domain. The
 *     barrier shatters instead of collapsing.</li>
 * </ul>
 *
 * <p>The core stays origin-agnostic: the routing decision lives in the
 * listener/lifecycle layer, the visual choice lives in the domain session.</p>
 */
public enum SessionEndReason {

    /** Ordinary end: expiry, quit or plugin shutdown. The barrier closes top-down. */
    NORMAL,

    /** The caster died inside the domain: the barrier shatters in fracture fronts. */
    CASTER_DEATH
}
