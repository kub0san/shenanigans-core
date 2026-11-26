package com.hizeric.shenaniganscore.battleground.combat;

import java.util.List;
import java.util.Locale;

/** Pure command-lockdown evaluation, shared by the listener and the diagnostic command. */
public final class CommandPolicy {

    /** Narrow in-mode whitelist: the mode's own commands plus messaging. */
    public static final java.util.List<String> DEFAULT_ALLOWED_ROOTS = java.util.List.of(
            "battleground", "bg", "ability", "ab", "leave",
            "msg", "r", "reply", "tell", "w", "whisper");

    /**
     * Roots that must never move an in-mode player out of the round. They are always blocked
     * in blacklist mode; in whitelist mode they are already refused by the whitelist.
     */
    public static final java.util.List<String> TELEPORT_BLOCKED_ROOTS = java.util.List.of(
            "back", "home", "spawn", "warp", "tp", "tpa", "tpahere", "world");

    /** Default blacklist: the plugin's pre-existing default plus every teleport root. */
    public static final java.util.List<String> DEFAULT_BLOCKED_ROOTS = concat(
            java.util.List.of("kit"), TELEPORT_BLOCKED_ROOTS);

    public enum Mode { WHITELIST, BLACKLIST }

    public record Input(Mode mode, List<String> allowedRoots, List<String> blockedRoots,
                        String command, boolean bypass) {
    }

    public record Decision(boolean allowed, String reason) {
    }

    private CommandPolicy() {
    }

    private static java.util.List<String> concat(java.util.List<String> base, java.util.List<String> extra) {
        java.util.List<String> out = new java.util.ArrayList<>(base);
        out.addAll(extra);
        return java.util.List.copyOf(out);
    }

    public static String root(String command) {
        if (command == null) return "";
        String text = command.trim();
        while (text.startsWith("/")) text = text.substring(1).trim();
        if (text.isEmpty()) return "";
        // Only the first token is the root; arguments after it are irrelevant.
        String root = text.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        return root;
    }

    /** Bare {@code /spawn} only: no arguments, no other root, any casing or namespace. */
    public static boolean isExactSpawn(String command) {
        if (command == null) return false;
        String text = command.trim();
        while (text.startsWith("/")) text = text.substring(1).trim();
        return "spawn".equals(root(command)) && text.split("\\s+").length == 1;
    }

    public static Decision evaluate(Input input) {
        if (input.bypass()) return new Decision(true, "admin bypass");
        String root = root(input.command());
        if (root.isEmpty()) return new Decision(false, "malformed command");
        if (input.mode() == Mode.WHITELIST) {
            boolean allowed = input.allowedRoots().stream()
                    .anyMatch(value -> root.equals(value.toLowerCase(Locale.ROOT)));
            return allowed ? new Decision(true, "whitelist: " + root)
                    : new Decision(false, "not in whitelist: " + root);
        }
        boolean blocked = input.blockedRoots().stream()
                .anyMatch(value -> root.equals(value.toLowerCase(Locale.ROOT)));
        return blocked ? new Decision(false, "blacklist: " + root)
                : new Decision(true, "not blacklisted: " + root);
    }
}
