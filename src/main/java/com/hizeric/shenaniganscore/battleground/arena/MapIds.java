package com.hizeric.shenaniganscore.battleground.arena;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Single source of truth for arena map ids.
 *
 * <p>An id is also a directory name under {@code battleground/arena/<id>/}, so callers must agree
 * on both shape and case: a lowercase {@code [a-z0-9][a-z0-9._-]*} id cannot traverse a path or
 * collide by case on a case-insensitive filesystem. Input is lowercased with {@link #normalize}
 * before {@link #isValid} is applied, so an admin's uppercase spelling is accepted but always
 * persisted and resolved as lowercase.</p>
 */
public final class MapIds {

    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9._-]*");

    private MapIds() { }

    /** True only for a non-empty lowercase id of {@code [a-z0-9][a-z0-9._-]*}. */
    public static boolean isValid(String id) {
        return id != null && VALID.matcher(id).matches();
    }

    /** Case/trim normalization applied before validation; {@code null} stays {@code null}. */
    public static String normalize(String id) {
        if (id == null) return null;
        return id.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Path-safety floor for the pure diff codec. Legacy logs may carry non-ASCII ids and must keep
     * round-tripping byte-for-byte, but no id may contain a separator, a dot-only segment or a
     * control character because that id is later used as a directory name.
     */
    public static boolean isPathSafe(String id) {
        if (id == null || id.isBlank()) return false;
        if (id.equals(".") || id.equals("..")) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (c == '/' || c == '\\' || Character.isISOControl(c)) return false;
        }
        return true;
    }
}
