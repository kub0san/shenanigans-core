package com.hizeric.shenaniganscore.resourcepack;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #48: the client-reachable pack HTTP server had no admission control (a socket flood could
 * exhaust file descriptors and spin WARNING logs) and never awaited its worker executor at shutdown.
 */
class PackHttpAdmissionTest {

    private static final Path SERVER = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "resourcepack", "ResourcePackManager.java");

    @Test
    void acceptLoopHasBoundedAdmissionAndReleasesPermits() throws IOException {
        assertTrue(Files.isRegularFile(SERVER), "expected source at " + SERVER.toAbsolutePath());
        String source = Files.readString(SERVER, StandardCharsets.UTF_8);

        assertTrue(source.contains("tryAcquire()"), "accept must refuse connections beyond the cap");
        assertTrue(source.contains("permits.release()"),
                "every admitted connection must release its permit on completion");
        assertTrue(source.contains("awaitTermination("),
                "shutdown must wait for in-flight workers instead of only cancelling them");
    }

    @Test
    void requestLineIsBoundedWithoutAnOffByOne() throws Exception {
        Method method = Class.forName("com.hizeric.shenaniganscore.resourcepack.ResourcePackManager$HttpServer")
                .getDeclaredMethod("readRequestLine", java.io.InputStream.class);
        method.setAccessible(true);
        byte[] oversized = new byte[9_000];
        java.util.Arrays.fill(oversized, (byte) 'a');
        String line = (String) method.invoke(null, new ByteArrayInputStream(oversized));
        assertFalse(line.length() > 8_192, "the request line buffer must not exceed 8192 bytes");
    }
}
