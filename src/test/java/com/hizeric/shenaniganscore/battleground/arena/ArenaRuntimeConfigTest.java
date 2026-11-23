package com.hizeric.shenaniganscore.battleground.arena;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaRuntimeConfigTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        ArenaRuntimeConfig.load(null, null);
    }

    private ArenaRuntimeConfig load(String name, String yaml) throws IOException {
        File target = new File(folder.toFile(), name);
        Files.writeString(target.toPath(), yaml, StandardCharsets.UTF_8);
        return ArenaRuntimeConfig.load(target, Logger.getLogger("test"));
    }

    private static String yaml(int duration, int warning, boolean pause, boolean protection,
                               boolean relight, boolean verify, boolean fallback,
                               int ms, int blocks, int chunks) {
        return """
                arena:
                  round:
                    duration-seconds: %d
                    warning-seconds: %d
                    pause-when-empty: %b
                  protection:
                    enabled: %b
                  restore:
                    relight: %b
                    verify: %b
                    fallback-on-unverified: %b
                    ms-per-tick: %d
                    blocks-per-tick: %d
                    chunks-per-tick: %d
                """.formatted(duration, warning, pause, protection, relight, verify,
                fallback, ms, blocks, chunks);
    }

    @Test
    void defaultsMatchTheShippedValues() {
        ArenaRuntimeConfig config = ArenaRuntimeConfig.defaults();
        assertEquals(1200, config.roundDurationSeconds());
        assertEquals(15, config.roundWarningSeconds());
        assertTrue(config.roundPauseWhenEmpty());
        assertTrue(config.protectionEnabled());
        assertTrue(config.restoreRelight());
        assertTrue(config.restoreVerify());
        assertTrue(config.restoreFallbackOnUnverified());
        assertEquals(30, config.restoreMsPerTick());
        assertEquals(256, config.restoreBlocksPerTick());
        assertEquals(4, config.restoreChunksPerTick());
    }

    @Test
    void validValuesAreReadFromTheYaml() throws IOException {
        ArenaRuntimeConfig config = load("config.yml",
                yaml(900, 30, false, false, false, false, false, 25, 128, 2));

        assertEquals(900, config.roundDurationSeconds());
        assertEquals(30, config.roundWarningSeconds());
        assertFalse(config.roundPauseWhenEmpty());
        assertFalse(config.protectionEnabled());
        assertFalse(config.restoreRelight());
        assertFalse(config.restoreVerify());
        assertFalse(config.restoreFallbackOnUnverified());
        assertEquals(25, config.restoreMsPerTick());
        assertEquals(128, config.restoreBlocksPerTick());
        assertEquals(2, config.restoreChunksPerTick());
    }

    @Test
    void outOfRangeNumbersFallBackToDefaults() throws IOException {
        ArenaRuntimeConfig config = load("config.yml",
                yaml(5, 99999, true, true, true, true, true, 500, 99999, 99999));

        assertEquals(1200, config.roundDurationSeconds());
        assertEquals(15, config.roundWarningSeconds());
        assertEquals(30, config.restoreMsPerTick());
        assertEquals(256, config.restoreBlocksPerTick());
        assertEquals(4, config.restoreChunksPerTick());
    }

    @Test
    void invalidTypesFallBackToDefaults() throws IOException {
        ArenaRuntimeConfig config = load("config.yml", """
                arena:
                  round:
                    duration-seconds: twenty-minutes
                    warning-seconds: 15.5
                    pause-when-empty: sometimes
                  protection:
                    enabled: nope
                  restore:
                    relight: 1
                    verify: false
                    ms-per-tick: fast
                    chunks-per-tick: 2.5
                """);

        assertEquals(1200, config.roundDurationSeconds());
        assertEquals(15, config.roundWarningSeconds());
        assertTrue(config.roundPauseWhenEmpty());
        assertTrue(config.protectionEnabled());
        assertTrue(config.restoreRelight());
        assertFalse(config.restoreVerify(), "a valid boolean must still be honoured");
        assertTrue(config.restoreFallbackOnUnverified());
        assertEquals(30, config.restoreMsPerTick());
        assertEquals(4, config.restoreChunksPerTick());
    }

    @Test
    void loadingASecondFileSwapsTheCurrentHolder() throws IOException {
        load("config.yml", yaml(600, 20, true, true, true, true, true, 20, 64, 1));
        assertEquals(600, ArenaRuntimeConfig.current().roundDurationSeconds());
        assertTrue(ArenaRuntimeConfig.current().protectionEnabled());

        load("config.yml", yaml(1800, 10, false, false, false, false, false, 40, 512, 8));
        assertEquals(1800, ArenaRuntimeConfig.current().roundDurationSeconds());
        assertFalse(ArenaRuntimeConfig.current().protectionEnabled());
        assertEquals(512, ArenaRuntimeConfig.current().restoreBlocksPerTick());
    }

    @Test
    void anUnreadableFileInstallsDefaultsAndKeepsCurrentNonNull() {
        ArenaRuntimeConfig config = ArenaRuntimeConfig.load(
                new File(folder.toFile(), "missing.yml"), Logger.getLogger("test"));

        assertNotNull(config);
        assertEquals(1200, config.roundDurationSeconds());
        assertNotNull(ArenaRuntimeConfig.current());
        assertEquals(1200, ArenaRuntimeConfig.current().roundDurationSeconds());
    }

    @Test
    void oldConfigWithDeferTrueLoadsWithoutBehaviourChange() throws IOException {
        ArenaRuntimeConfig config = load("config.yml", yaml(1200, 15, true, true, true, true, true, 30, 256, 4)
                .replace("    chunks-per-tick: 4\n",
                         "    chunks-per-tick: 4\n    defer-near-player: true\n"));
        assertEquals(256, config.restoreBlocksPerTick());
        assertEquals(4, config.restoreChunksPerTick());
        assertTrue(config.restoreVerify());
    }

    @Test
    void deferNearPlayerKeyAbsentLoadsWithShippedDefaults() throws IOException {
        ArenaRuntimeConfig config = load("config.yml", yaml(1200, 15, true, true, true, true, true, 30, 256, 4));
        assertEquals(30, config.restoreMsPerTick());
        assertEquals(256, config.restoreBlocksPerTick());
        assertEquals(4, config.restoreChunksPerTick());
    }

    @Test
    void deferNearPlayerKeyValueIsIgnored() throws IOException {
        ArenaRuntimeConfig config = load("config.yml", yaml(1200, 15, true, true, true, true, true, 30, 256, 4)
                .replace("    chunks-per-tick: 4\n",
                         "    chunks-per-tick: 4\n    defer-near-player: false\n"));
        assertEquals(30, config.restoreMsPerTick(), "an old false value changes no restore tuning");
        assertNotNull(ArenaRuntimeConfig.current());
    }
}
