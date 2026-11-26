package com.hizeric.shenaniganscore.resourcepack;

import com.hizeric.shenaniganscore.battleground.indicator.TechniqueGlyphs;
import com.hizeric.shenaniganscore.battleground.indicator.TechniqueGlyphs.State;
import com.hizeric.shenaniganscore.battleground.indicator.TechniqueGlyphs.Technique;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #50: the bundled pack and the Java-side glyph/item-model contracts must not drift.
 * Paths are relative to the Gradle test working directory (the project root).
 */
class ResourcePackContractTest {

    // Byte-exact pin: if Java or the atlas moves this glyph, the test fails instead of following.
    private static final char EMPTY_GLYPH = '\uE0FD';

    private static final Path HUD_JSON =
            Path.of("resourcepack", "assets", "shenanigans", "font", "hud.json");
    private static final Path MODEL_DIR =
            Path.of("resourcepack", "assets", "shenanigans", "models", "item");
    private static final Path JAVA_ROOT = Path.of("src", "main", "java");

    private static final Pattern ROW = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern UNICODE_ESCAPE = Pattern.compile("\\\\u([0-9a-fA-F]{4})");
    // Model names passed to GuiTheme.model(...)/Key.key("shenanigans", name), e.g. ability_blue.
    private static final Pattern MODEL_NAME = Pattern.compile("\"((?:ability|ultimate)_[a-z0-9_]+)\"");

    @Test
    void hudJsonDeclaresEveryJavaGlyphAtExactCodepoints() throws IOException {
        String declared = parseGlyphs(Files.readString(HUD_JSON, StandardCharsets.UTF_8));

        StringBuilder exact = new StringBuilder();
        for (int cp = 0xE000; cp <= 0xE0FD; cp++) {
            exact.append((char) cp);
        }
        assertEquals(exact.toString(), declared,
                "hud.json must declare exactly U+E000..U+E0FD, ascending, with no gaps");

        for (Technique technique : Technique.values()) {
            assertDeclared(declared, technique.glyph(State.READY, 0), technique.canonicalId() + " READY");
            assertDeclared(declared, technique.glyph(State.LOCKED, 0), technique.canonicalId() + " LOCKED");
            for (int level = 0; level <= 20; level++) {
                assertDeclared(declared, technique.glyph(State.COOLDOWN, level),
                        technique.canonicalId() + " COOLDOWN " + level);
            }
        }
        assertDeclared(declared, EMPTY_GLYPH, "EMPTY");
        assertEquals(EMPTY_GLYPH, TechniqueGlyphs.EMPTY, "empty glyph drifted in Java");
    }

    @Test
    void everyJavaItemModelResolvesToPackModelFile() throws IOException {
        Set<String> modelNames = new TreeSet<>();
        try (Stream<Path> files = Files.walk(JAVA_ROOT)) {
            for (Path source : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher = MODEL_NAME.matcher(Files.readString(source, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    modelNames.add(matcher.group(1));
                }
            }
        }
        assertFalse(modelNames.isEmpty(), "no item model names found in Java sources - scan is broken");

        for (String name : modelNames) {
            Path model = MODEL_DIR.resolve(name + ".json");
            assertTrue(Files.isRegularFile(model),
                    "shenanigans:" + name + " is used in Java but " + model + " is missing");
        }
    }

    private static void assertDeclared(String declared, char glyph, String use) {
        assertTrue(declared.indexOf(glyph) >= 0,
                String.format("U+%04X (%s) is used by TechniqueGlyphs but absent from %s",
                        (int) glyph, use, HUD_JSON));
    }

    /** Minimal JSON scan: take the chars array, decode unicode escapes, flatten its rows. */
    private static String parseGlyphs(String json) {
        int key = json.indexOf("\"chars\"");
        assertTrue(key >= 0, HUD_JSON + " has no chars provider");
        int open = json.indexOf('[', key);
        int close = json.indexOf(']', open);
        assertTrue(open >= 0 && close > open, HUD_JSON + " has no chars array");

        Matcher rows = ROW.matcher(json.substring(open + 1, close));
        StringBuilder flat = new StringBuilder();
        while (rows.find()) {
            flat.append(decode(rows.group(1)));
        }
        assertFalse(flat.isEmpty(), HUD_JSON + " chars array has no glyph rows");
        return flat.toString();
    }

    private static String decode(String raw) {
        Matcher escapes = UNICODE_ESCAPE.matcher(raw);
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        while (escapes.find()) {
            out.append(raw, cursor, escapes.start());
            out.append((char) Integer.parseInt(escapes.group(1), 16));
            cursor = escapes.end();
        }
        return out.append(raw, cursor, raw.length()).toString();
    }
}
