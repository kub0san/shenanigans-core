package com.hizeric.shenaniganscore.battleground;

/**
 * Issue #7 readiness decision: "ready" means the FULL restore finished, not just tier 1.
 * Kept free of Bukkit types so the deploy-open boundary and its copy are testable without a
 * server. Instance state is two independent one-shots armed by {@link #reset()} each round;
 * the static methods are the single source of readiness copy.
 */
public final class RoundReadiness {

    public enum Outcome {
        NONE,
        ANNOUNCE_PENDING,
        OPEN_CLEAN,
        OPEN_DIRTY
    }

    private boolean tierOneSeen;
    private boolean opened;

    /** Arms a fresh readiness generation for the next reset. */
    public void reset() {
        tierOneSeen = false;
        opened = false;
    }

    /**
     * Tier-1 gate: critical blocks are restored, so a status announcement is pending, but deploy
     * stays closed until the full restore finishes. One-shot per reset; never sets {@link #opened}.
     */
    public Outcome onTierOneDone() {
        if (tierOneSeen) return Outcome.NONE;
        tierOneSeen = true;
        return Outcome.ANNOUNCE_PENDING;
    }

    /**
     * Full restore reached its terminal state: opens deploy exactly once per reset, clean or
     * dirty. Sets both one-shots on the first call so a late tier-1 gate cannot re-announce;
     * a late completion with deployed players returns {@link Outcome#NONE} and changes nothing.
     */
    public Outcome onRestoreFinished(boolean failed) {
        if (opened) return Outcome.NONE;
        opened = true;
        tierOneSeen = true;
        return failed ? Outcome.OPEN_DIRTY : Outcome.OPEN_CLEAN;
    }

    /** Whether the full restore has opened deploy for this generation. */
    public boolean opened() {
        return opened;
    }

    /** Whether the tier-1 gate (or the full restore) has been seen for this generation. */
    public boolean tierOneSeen() {
        return tierOneSeen;
    }

    /**
     * Completion broadcast. A clean restore claims ready; a failed restore opens deploy with a
     * dirty-arena warning instead — failure is not a strand, but it must not claim ready.
     */
    public static String announcement(int roundNumber, boolean failed) {
        if (failed) {
            return "<red>Restore failed — the arena may be dirty. <yellow>Deploy with /battleground join at your own risk. <gray>Restoration will be retried next round.";
        }
        return "<green>Round <white>" + roundNumber + "<green> is ready — deploy with /battleground join";
    }

    /** Tier-1 status: critical blocks are back, but no message says ready before completion. */
    public static String statusAnnouncement() {
        return "<gray>Critical blocks restored — verifying the arena. Deployment opens when the restore finishes.";
    }

    /** Join-attempt feedback while the gate is closed; names the phase the player is waiting on. */
    public static String waitingMessage(RoundState state) {
        if (state == RoundState.STAGING) {
            return "<yellow>The arena is preparing (tier 1). Deployment opens when the restore finishes.";
        }
        return "<yellow>The arena is still restoring (verification and block writes). Deployment opens when it finishes.";
    }
}
