package com.hizeric.shenaniganscore.core.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.time.Duration;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Shared typewriter title animation used by domain announcements.
 *
 * <p>Text is processed by Unicode code point rather than UTF-16 {@code char},
 * so supplementary characters are never split in half. The glitch glyph is
 * chosen deterministically for a title, preventing it from jumping to a new
 * character every time the session re-renders the title.</p>
 */
public final class TitleAnimator {

    /** Full-width space keeps the title's layout stable while text arrives. */
    private static final int HOLDER = '\u3000';
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    /** Seconds between glyphs of the main line. */
    public static final double MAIN_STEP = 0.12;
    /** Seconds before the secondary name starts typing. */
    public static final double SUB_DELAY = 0.55;
    /** Seconds between glyphs of the secondary name. */
    public static final double SUB_STEP = 0.10;

    /** One in N completed titles receives one deterministic obfuscated glyph. */
    private static final int GLITCH_CHANCE = 3;
    private static final long HOLD_MS = 350L;
    private static final long HOLD_LAST_MS = 900L;
    private static final long FADE_LAST_MS = 500L;

    private TitleAnimator() {
    }

    /** Number of main-line Unicode code points visible at elapsed time {@code t}. */
    public static int revealedMain(String main, double t) {
        return revealCount(main, t, MAIN_STEP, 0.0);
    }

    /** Number of secondary-name Unicode code points visible at elapsed time {@code t}. */
    public static int revealedSub(String sub, double t) {
        return revealCount(sub, t, SUB_STEP, SUB_DELAY);
    }

    /**
     * Builds the animated title.
     *
     * @param main    main line, normally 領域展開
     * @param sub     domain name in kanji
     * @param english English name appended after the kanji finishes
     * @param t       seconds since the sign phase began
     * @param last    whether this is the final render of the title
     */
    public static Title build(String main, String sub, String english, double t, boolean last) {
        String safeMain = Objects.requireNonNullElse(main, "");
        String safeSub = Objects.requireNonNullElse(sub, "");
        String safeEnglish = Objects.requireNonNullElse(english, "");
        double elapsed = Double.isNaN(t) ? 0.0 : Math.max(0.0, t);

        int mainCount = codePointCount(safeMain);
        int subCount = codePointCount(safeSub);
        int mainRevealed = revealedMain(safeMain, elapsed);
        int subRevealed = revealedSub(safeSub, elapsed);
        boolean complete = subRevealed >= subCount;

        int glitchIndex = complete ? chooseGlitch(safeMain, safeSub, safeEnglish, mainCount) : -1;

        // Escape all domain-provided glyphs before composing MiniMessage markup.
        // This keeps custom names literal rather than allowing them to inject tags.
        Component mainLine = MINI_MESSAGE.deserialize(
                "<#d8d8e8><bold>" + reveal(safeMain, mainRevealed, glitchIndex)
                        + "</bold></#d8d8e8>");

        String subMarkup = "<dark_red>" + reveal(safeSub, subRevealed, -1);
        if (complete && !safeEnglish.isBlank()) {
            subMarkup += " <gray><italic>" + escapeLiteral(safeEnglish)
                    + "</italic></gray>";
        }
        Component subLine = MINI_MESSAGE.deserialize(subMarkup + "</dark_red>");

        return Title.title(
                mainLine,
                subLine,
                Title.Times.times(
                        Duration.ZERO,
                        Duration.ofMillis(last ? HOLD_LAST_MS : HOLD_MS),
                        Duration.ofMillis(last ? FADE_LAST_MS : 0L)
                )
        );
    }

    /** Reveals the first {@code n} glyphs, holding the remaining layout with full-width spaces. */
    public static String reveal(String full, int n) {
        return reveal(full, n, -1);
    }

    /**
     * String-form reveal helper for callers that still compose MiniMessage text.
     * User/domain text is escaped before it is inserted into markup.
     */
    public static String reveal(String full, int n, int glitchIndex) {
        String safe = Objects.requireNonNullElse(full, "");
        int count = codePointCount(safe);
        int visible = Math.max(0, Math.min(count, n));

        StringBuilder output = new StringBuilder(safe.length() + count * 2);
        for (int index = 0, offset = 0; index < count; index++) {
            int codePoint = safe.codePointAt(offset);
            String glyph = new String(Character.toChars(codePoint));
            offset += Character.charCount(codePoint);

            if (index >= visible) {
                output.appendCodePoint(HOLDER);
            } else if (index == glitchIndex) {
                output.append("<obfuscated>")
                        .append(escapeLiteral(glyph))
                        .append("</obfuscated>");
            } else {
                output.append(escapeLiteral(glyph));
            }
        }
        return output.toString();
    }

    /** Escapes MiniMessage's two plain-text control characters. */
    private static String escapeLiteral(String value) {
        return value.replace("\\", "\\\\").replace("<", "\\<");
    }

    private static int revealCount(String value, double time, double step, double delay) {
        int count = codePointCount(value);
        if (count == 0 || Double.isNaN(time) || time < delay) {
            return 0;
        }
        double elapsed = Math.max(0.0, time - delay);
        double arrived = Math.floor(elapsed / step) + 1.0;
        return (int) Math.min(count, Math.max(0.0, arrived));
    }

    private static int codePointCount(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    private static int chooseGlitch(String main, String sub, String english, int mainCount) {
        if (mainCount == 0) {
            return -1;
        }
        long seed = 0x9E3779B97F4A7C15L;
        seed = mix(seed ^ main.hashCode());
        seed = mix(seed ^ ((long) sub.hashCode() << 1));
        seed = mix(seed ^ ((long) english.hashCode() << 2));

        SplittableRandom random = new SplittableRandom(seed);
        return random.nextInt(GLITCH_CHANCE) == 0 ? random.nextInt(mainCount) : -1;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
