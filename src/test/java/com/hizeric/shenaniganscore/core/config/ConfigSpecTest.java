package com.hizeric.shenaniganscore.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSpecTest {

    @Test
    void aBlankPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigSpec("", List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new ConfigSpec("   ", List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new ConfigSpec(null, List.of(), 1));
    }

    @Test
    void aNegativeVersionIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigSpec("config.yml", List.of(), -1));
    }

    @Test
    void zeroIsAValidVersion() {
        ConfigSpec spec = new ConfigSpec("config.yml", List.of(), 0);
        assertEquals(0, spec.version());
    }

    @Test
    void obsoleteKeysAreImmutableAndNeverNull() {
        ConfigSpec spec = new ConfigSpec("config.yml", List.of("a.b", "c"), 1);
        assertThrows(UnsupportedOperationException.class, () -> spec.obsoleteKeys().add("d.e"));
        assertEquals(2, spec.obsoleteKeys().size());
        assertTrue(new ConfigSpec("config.yml", null, 1).obsoleteKeys().isEmpty());
    }
}
