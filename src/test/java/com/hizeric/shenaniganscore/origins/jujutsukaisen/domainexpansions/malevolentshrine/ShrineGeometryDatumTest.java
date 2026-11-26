package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #75: the shrine centre is the caster's block Y + 1, while the real
 * platform surface is one block lower. Model coordinates are authored with
 * {@code y = 0} at the ground, so placing them at {@code centreY + localY}
 * floats every ground-contact prop one block above the platform.
 *
 * <p>The fix is a single pure datum seam used by {@code Builder.toWorld()}.
 * The shared display pivot is untouched; ground-contact props are seated
 * against it in {@code ShrineGeometry} / {@code Builder}.</p>
 */
class ShrineGeometryDatumTest {

    @Test
    void localZeroSitsOnThePlatformSurface() {
        assertEquals(63.0, ShrineGeometry.groundContactY(64.0, 0.0),
                "local y=0 must resolve to shrine centre minus one block");
        assertEquals(72.5, ShrineGeometry.groundContactY(73.5, 0.0));
    }

    @Test
    void everyModelYShiftedByExactlyOneBlock() {
        for (double localY : new double[] {-1.5, 0.0, 0.45, 2.25, 21.0, 23.0}) {
            assertEquals(64.0 - 1.0 + localY, ShrineGeometry.groundContactY(64.0, localY),
                    "only the datum may change; the local offset must survive verbatim");
        }
    }

    @Test
    void toWorldUsesTheDatumAndFloorStaysOnItsOwnDatum() throws IOException {
        Path builder = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "origins", "jujutsukaisen", "domainexpansions", "malevolentshrine", "Builder.java");
        String source = Files.readString(builder, StandardCharsets.UTF_8);
        assertTrue(source.contains("ShrineGeometry.groundContactY(c.getY(), y)"),
                "toWorld must derive model Y from the ground datum seam");

        Path floor = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "origins", "jujutsukaisen", "domainexpansions", "malevolentshrine", "Floor.java");
        String floorSource = Files.readString(floor, StandardCharsets.UTF_8);
        assertTrue(floorSource.contains("center.setY(session.getShrineCenter().getY() - 1.0);"),
                "the floor's existing datum must not be changed by this fix");
    }
}
