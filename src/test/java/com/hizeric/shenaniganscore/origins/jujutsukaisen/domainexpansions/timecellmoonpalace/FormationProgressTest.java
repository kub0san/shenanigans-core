package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Issue #76: the barrier formation front was quantised into 24 steps, so the
 * floor and shell advanced in visible jumps. The 24 FPS clock is a film motif,
 * not a barrier rule; the front itself must be continuous.
 */
class FormationProgressTest {

    @Test
    void subFrameProgressIsPreserved() {
        assertEquals(1.0 / 48.0, FormationProgress.continuous(1.0 / 48.0),
                "half a frame must not be rounded away");
        assertEquals(0.999, FormationProgress.continuous(0.999),
                "the last frame must not jump straight to fully formed");
        assertEquals(0.0, FormationProgress.continuous(0.0));
        assertEquals(1.0, FormationProgress.continuous(1.0));
    }

    @Test
    void outOfRangeProgressIsClamped() {
        assertEquals(0.0, FormationProgress.continuous(-0.5));
        assertEquals(1.0, FormationProgress.continuous(1.5));
    }

    @Test
    void theSessionUsesTheContinuousSeam() throws IOException {
        Path session = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "origins", "jujutsukaisen", "domainexpansions", "timecellmoonpalace", "Session.java");
        String source = Files.readString(session, StandardCharsets.UTF_8);
        assertFalse(source.contains("stepped("), "the 24-step quantiser must be gone");
        assertEquals(2, count(source, "setFormationProgress(FormationProgress.continuous("),
                "both expansion and collapse must feed the continuous front");
    }

    private static int count(String source, String needle) {
        int n = 0;
        for (int at = source.indexOf(needle); at >= 0; at = source.indexOf(needle, at + 1)) {
            n++;
        }
        return n;
    }
}
