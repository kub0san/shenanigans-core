package com.hizeric.shenaniganscore.battleground.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small null-safe coercion helpers shared by the SQL repositories and {@link MapCodec}.
 *
 * <p>Storage blobs decode to loosely-typed {@code Map<String, Object>} values (numbers may arrive
 * as {@link Long}, lists as raw {@link List}, nested maps with non-string keys). These helpers give
 * every reader the same defensive behaviour so a hand-edited or legacy blob cannot throw a
 * {@link ClassCastException} at read time.</p>
 */
final class CodecUtil {

    private CodecUtil() { }

    /** Reads an integer, tolerating any {@link Number} or a stringified number. Wrong-typed values fail loudly. */
    static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null) return 0;
        if (value instanceof String text) {
            try { return Integer.parseInt(text.trim()); }
            catch (NumberFormatException ignored) { /* fall through */ }
        }
        throw new IllegalArgumentException("Expected a number, got "
                + value.getClass().getSimpleName() + ": " + value);
    }

    /** Reads a string, mapping {@code null} to empty and stringifying anything else. */
    static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Reads a list of strings, stringifying each element and dropping non-lists entirely. */
    static List<String> strings(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) result.add(item == null ? "" : String.valueOf(item));
        return result;
    }

    /** Rekeys a decoded map to {@code String}, stringifying every key. */
    static Map<String, Object> stringMap(Map<?, ?> map) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            result.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
