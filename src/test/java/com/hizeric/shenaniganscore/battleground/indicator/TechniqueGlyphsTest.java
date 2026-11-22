package com.hizeric.shenaniganscore.battleground.indicator;

import com.hizeric.shenaniganscore.battleground.indicator.TechniqueGlyphs.State;
import com.hizeric.shenaniganscore.battleground.indicator.TechniqueGlyphs.Technique;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TechniqueGlyphsTest {

    private static final String[] IDS = {
            "jjk:blue", "jjk:red", "jjk:purple", "jjk:fuga", "jjk:piercing_blood",
            "jjk:world_slash", "jjk:malevolent_shrine", "jjk:unlimited_void",
            "jjk:time_cell_moon_palace", "jjk:womb_profusion", "minecraft:dash_sword"
    };

    private static final char[] BLOCK_STARTS = {
            '\uE000', '\uE017', '\uE02E', '\uE045', '\uE05C', '\uE073',
            '\uE08A', '\uE0A1', '\uE0B8', '\uE0CF', '\uE0E6'
    };

    @Test
    void fontKeyAndEmptyHaveExactContractValues() {
        assertEquals("shenanigans", TechniqueGlyphs.FONT_KEY.namespace());
        assertEquals("hud", TechniqueGlyphs.FONT_KEY.value());
        assertEquals('\uE0FD', TechniqueGlyphs.EMPTY);
    }

    @Test
    void statesAreExactlyReadyLockedCooldown() {
        State[] states = State.values();
        assertEquals(3, states.length);
        assertEquals(State.READY, states[0]);
        assertEquals(State.LOCKED, states[1]);
        assertEquals(State.COOLDOWN, states[2]);
    }

    @Test
    void byIdResolvesEveryCanonicalId() {
        for (int i = 0; i < IDS.length; i++) {
            Technique t = TechniqueGlyphs.byId(IDS[i]);
            assertNotNull(t, IDS[i]);
            assertEquals(IDS[i], t.canonicalId());
        }
    }

    @Test
    void byIdIsCaseInsensitive() {
        assertNotNull(TechniqueGlyphs.byId("JJK:BLUE"));
        assertNotNull(TechniqueGlyphs.byId("MINECRAFT:DASH_SWORD"));
    }

    @Test
    void byIdRejectsNullUnknownAndSubstrings() {
        org.junit.jupiter.api.Assertions.assertNull(TechniqueGlyphs.byId(null));
        org.junit.jupiter.api.Assertions.assertNull(TechniqueGlyphs.byId(""));
        org.junit.jupiter.api.Assertions.assertNull(TechniqueGlyphs.byId("unknown"));
        org.junit.jupiter.api.Assertions.assertNull(TechniqueGlyphs.byId("blue"));
        org.junit.jupiter.api.Assertions.assertNull(TechniqueGlyphs.byId("jjk:blueprint"));
    }

    @Test
    void glyphMapsEveryTechniqueAndStateIncludingCooldownEnds() {
        for (int i = 0; i < IDS.length; i++) {
            Technique t = TechniqueGlyphs.byId(IDS[i]);
            char start = BLOCK_STARTS[i];
            assertEquals(start, t.glyph(State.READY, 0));
            assertEquals((char) (start + 1), t.glyph(State.LOCKED, 0));
            assertEquals((char) (start + 2), t.glyph(State.COOLDOWN, 0));
            assertEquals((char) (start + 22), t.glyph(State.COOLDOWN, 20));
            for (int level = 0; level <= 20; level++) {
                assertEquals((char) (start + 2 + level), t.glyph(State.COOLDOWN, level));
            }
        }
    }

    @Test
    void glyphClampsCooldownLevel() {
        Technique t = TechniqueGlyphs.byId(IDS[0]);
        assertEquals(t.glyph(State.COOLDOWN, 0), t.glyph(State.COOLDOWN, -5));
        assertEquals(t.glyph(State.COOLDOWN, 20), t.glyph(State.COOLDOWN, 21));
    }

    @Test
    void allTwoHundredFiftyFourGlyphsAreDistinct() {
        Set<Character> glyphs = new HashSet<>();
        for (String id : IDS) {
            Technique t = TechniqueGlyphs.byId(id);
            assertTrue(glyphs.add(t.glyph(State.READY, 0)));
            assertTrue(glyphs.add(t.glyph(State.LOCKED, 0)));
            for (int level = 0; level <= 20; level++) {
                assertTrue(glyphs.add(t.glyph(State.COOLDOWN, level)));
            }
        }
        assertTrue(glyphs.add(TechniqueGlyphs.EMPTY));
        assertEquals(254, glyphs.size());
    }
}