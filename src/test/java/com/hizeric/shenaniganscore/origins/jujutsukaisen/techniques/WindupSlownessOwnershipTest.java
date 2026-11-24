package com.hizeric.shenaniganscore.origins.jujutsukaisen.techniques;

import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #37 (SP-C-002 / SP-C-004): overlapping windups must not let an older cast restore its root
 * over a newer one, and World Slash's chant root must be owned by the shared cleanup helper so a
 * death/logout cancel cannot leave the caster rooted.
 */
class WindupSlownessOwnershipTest {

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void anOlderCastCannotRestoreItsRootOverANewerCast() {
        ServerMock server = MockBukkit.mock();
        PlayerMock player = server.addPlayer();
        WindupSlowness older = new WindupSlowness();
        WindupSlowness newer = new WindupSlowness();

        WindupSlownessCleanup.grant(player, older, 100, 2);
        WindupSlownessCleanup.grant(player, newer, 80, 3);
        WindupSlownessCleanup.clear(player, older);

        PotionEffect live = player.getPotionEffect(PotionEffectType.SLOWNESS);
        assertNotNull(live, "the newer cast's root must survive the older cast's cleanup");
        assertEquals(3, live.getAmplifier());

        WindupSlownessCleanup.clear(player, newer);
        assertNull(player.getPotionEffect(PotionEffectType.SLOWNESS),
                "the owning cast's cleanup must remove the root");
    }

    @Test
    void worldSlashChantOwnsItsRootOnEveryExitPath() throws IOException {
        Path path = Path.of("src", "main", "java", "com", "hizeric", "shenaniganscore",
                "origins", "jujutsukaisen", "techniques", "worldslash", "WorldSlashCast.java");
        assertTrue(Files.isRegularFile(path), "expected source at " + path.toAbsolutePath());
        String source = Files.readString(path, StandardCharsets.UTF_8);

        assertTrue(source.contains("WindupSlownessCleanup.grant("),
                "the chant must grant its root through the shared owner-aware helper");
        int clears = 0;
        for (int i = source.indexOf("clearWindupSlowness()"); i >= 0;
             i = source.indexOf("clearWindupSlowness()", i + 1)) clears++;
        assertTrue(clears >= 3,
                "the chant root must be cleared on exception, death/logout cancel and normal completion");
    }
}
