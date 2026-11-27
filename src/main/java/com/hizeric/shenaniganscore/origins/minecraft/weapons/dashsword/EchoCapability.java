package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

/**
 * Idempotent capability flag for the mannequin echo renderer.
 *
 * <p>Pure: no Bukkit or PacketEvents types in its API. The renderer trips it once a packet send
 * fails and thereafter treats the echo capability as disabled, so a broken metadata/packet path
 * cannot be retried every tick. {@link #trip(Throwable)} reports the transition only on the first
 * failure; {@link #reset()} re-enables for tests and shutdown.</p>
 */
final class EchoCapability {

    private boolean disabled;

    /** @return {@code true} only on the first failure (the disable transition). */
    boolean trip(Throwable failure) {
        if (disabled) return false;
        disabled = true;
        return true;
    }

    boolean disabled() {
        return disabled;
    }

    /** Re-enable for tests and shutdown. */
    void reset() {
        disabled = false;
    }
}