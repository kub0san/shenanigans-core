package com.hizeric.shenaniganscore.battleground.player;

import org.bukkit.GameMode;

import java.util.Locale;
import java.util.Set;

/**
 * The single definition of "safe values after staging".
 *
 * <p>These are asserted on exit rather than copied from a snapshot. A snapshot taken while a
 * previous staging pass had already leaked a flag simply re-applies the leak on the next exit,
 * which is how players ended up permanently invulnerable after removing the plugin.</p>
 */
public final class StagingContract {

    private static final Set<String> ALLOWED_ROOTS = Set.of(
            "battleground", "bg", "ability", "ab");

    private StagingContract() { }

    public record ExitState(boolean invulnerable, boolean invisible, boolean collidable,
                            boolean allowFlight, boolean flying, GameMode gameMode) { }

    /** Player state observed at startup; the in-memory lock cannot survive a restart. */
    public record LeakFlags(boolean inArenaWorld, boolean allowFlight, boolean flying,
                            boolean invulnerable, boolean invisible, boolean collidable,
                            boolean spectator, boolean admin) { }

    /** Detects a staging flag that leaked into player.dat before a crash or hot reload. */
    public static boolean needsRepairAfterRestart(LeakFlags flags) {
        if (!flags.inArenaWorld() || flags.admin()) return false;
        return flags.allowFlight() || flags.flying() || flags.invulnerable()
                || flags.invisible() || !flags.collidable() || flags.spectator();
    }

    public static ExitState exitState() {
        return new ExitState(false, false, true, false, false, GameMode.SURVIVAL);
    }

    /** Never reset flags for an ordinary player who just logged in. */
    public static boolean needsRepair(boolean staged, boolean hasSnapshot) {
        return staged || hasSnapshot;
    }

    /**
     * Exiting staging may only assert known-good flags for a player this mode actually staged.
     * An ordinary player who opened the hub and clicked Close must keep creative/flight untouched.
     */
    public static boolean shouldExit(boolean hasSnapshot, boolean staged) {
        return needsRepair(staged, hasSnapshot);
    }

    /** Allows the mode's own commands while a player is locked in staging. */
    public static boolean isAllowedCommand(String message) {
        if (message == null) return false;
        String text = message.trim();
        while (text.startsWith("/")) text = text.substring(1).trim();
        if (text.isEmpty()) return false;
        String root = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        return ALLOWED_ROOTS.contains(root);
    }
}
