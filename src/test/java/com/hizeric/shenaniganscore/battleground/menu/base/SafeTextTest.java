package com.hizeric.shenaniganscore.battleground.menu.base;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Config-supplied names must never be parsed as markup. */
class SafeTextTest {

    @Test
    void aTagInAConfigNameIsEscaped() {
        String raw = "<red>BLUE</red>";
        String safe = SafeText.escape(raw);
        assertNotEquals(raw, safe);
        assertDoesNotThrow(() -> MiniMessage.miniMessage().deserialize(safe));
        String plain = MiniMessage.miniMessage().stripTags(safe);
        assertTrue(plain.contains("BLUE"), plain);
    }

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", SafeText.escape(null));
    }
}
