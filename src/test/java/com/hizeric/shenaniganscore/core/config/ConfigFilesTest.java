package com.hizeric.shenaniganscore.core.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigFilesTest {

    private static final byte[] BUNDLED = """
            config-version: 1
            fresh-key: true
            """.getBytes(StandardCharsets.UTF_8);

    private static final ConfigSpec SPEC = new ConfigSpec("config.yml", List.of("stale-key"), 1);

    @TempDir Path folder;

    private File file(String name) {
        return new File(folder.toFile(), name);
    }

    @Test
    void aMissingFileIsExtractedFromTheBundledCopy() throws IOException {
        File target = file("config.yml");
        assertFalse(target.exists());

        assertTrue(ConfigFiles.updateFile(target, BUNDLED, SPEC, Logger.getLogger("test")));

        assertTrue(target.exists());
        assertArrayEquals(BUNDLED, Files.readAllBytes(target.toPath()));
    }

    @Test
    void backsUpThePreChangeBytesWhenItRewrites() throws IOException {
        File target = file("config.yml");
        String before = "stale-key: 1\nadmin-tuned: 99\n";
        Files.writeString(target.toPath(), before);

        assertTrue(ConfigFiles.updateFile(target, BUNDLED, SPEC, Logger.getLogger("test")));

        File backup = new File(target.getPath() + ".backup");
        assertTrue(backup.exists(), "backup written before the rewrite");
        assertEquals(before, Files.readString(backup.toPath()));
        String after = Files.readString(target.toPath());
        assertFalse(after.contains("stale-key"), "obsolete key removed");
        assertTrue(after.contains("admin-tuned: 99"), "admin value preserved");
        assertTrue(after.contains("fresh-key"), "bundled key added");
        assertTrue(after.contains("config-version: 1"));
    }

    @Test
    void aSecondRunChangesNothingAndKeepsTheBackupByteForByte() throws IOException {
        File target = file("config.yml");
        Files.writeString(target.toPath(), "stale-key: 1\n");

        ConfigFiles.updateFile(target, BUNDLED, SPEC, Logger.getLogger("test"));
        byte[] backupBytes = Files.readAllBytes(new File(target.getPath() + ".backup").toPath());

        assertFalse(ConfigFiles.updateFile(target, BUNDLED, SPEC, Logger.getLogger("test")),
                "second run must be a no-op");
        assertArrayEquals(backupBytes, Files.readAllBytes(new File(target.getPath() + ".backup").toPath()));
    }

    @Test
    void anUnparseableFileIsReplacedByTheBundledCopyAndBackedUp() throws IOException {
        File target = file("config.yml");
        Files.writeString(target.toPath(), "{{{{ not valid yaml: : :");

        assertTrue(ConfigFiles.updateFile(target, BUNDLED, SPEC, Logger.getLogger("test")));

        assertArrayEquals(BUNDLED, Files.readAllBytes(target.toPath()));
        assertEquals("{{{{ not valid yaml: : :",
                Files.readString(new File(target.getPath() + ".backup").toPath()));
    }

    @Test
    void aFileThatIsAlreadyCurrentNeedsNoRewrite() throws IOException {
        File target = file("config.yml");
        Files.writeString(target.toPath(), new String(BUNDLED, StandardCharsets.UTF_8));

        assertFalse(ConfigFiles.updateFile(target, BUNDLED, SPEC, Logger.getLogger("test")),
                "an already-current file needs no rewrite");
        assertTrue(Files.readString(target.toPath()).contains("fresh-key"));
    }
}
