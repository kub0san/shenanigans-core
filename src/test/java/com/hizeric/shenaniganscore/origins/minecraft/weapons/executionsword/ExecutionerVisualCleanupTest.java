package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Issue #61: no named title/glyph FX, and no cutscene state outliving a disable. */
class ExecutionerVisualCleanupTest {

    private static final Path EFFECT = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "origins", "minecraft", "weapons", "executionsword",
            "ExecutionerSwordEffect.java");
    private static final Path CORE = Path.of("src", "main", "java", "com", "hizeric",
            "shenaniganscore", "ShenanigansCore.java");

    @AfterEach
    void unmock() {
        MockBukkit.unmock();
    }

    private static String source(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + needle.length())) {
            n++;
        }
        return n;
    }

    @Test
    void neitherTimelineRendersANamedTitleOrJapaneseGlyph() throws IOException {
        String effect = source(EFFECT);
        assertFalse(effect.contains("TitleAnimator"), "no named title sequence may be built");
        assertFalse(effect.contains("showTitle"), "an effect must not take the title slot");
        assertFalse(effect.contains("spawnGlyph"), "no glyph displays");
        assertFalse(effect.contains("TechniqueDisplayOps"), "no JJK display helper in a Minecraft-origin FX");
        assertFalse(effect.contains("斬"));
        assertFalse(effect.contains("終末の裁き"));
    }

    @Test
    void gameplayConstantsAndDamageStayByteForByte() throws IOException {
        String effect = source(EFFECT);
        assertTrue(effect.contains("CUTSCENE_TICKS = 24"));
        assertTrue(effect.contains("tick >= 10"), "uppercut stays ten ticks");
        assertTrue(effect.contains("target.damage(12.0, caster)"));
        assertTrue(effect.contains("double mark = Math.min(0.5, target.getHealth() - 0.1)"));
        assertTrue(effect.contains("target.damage(mark, caster)"));
        assertTrue(effect.contains("target.damage(dmg, caster)"));
    }

    @Test
    void everyAbortPathAndTheNaturalEndFinishTheTimeline() throws IOException {
        String effect = source(EFFECT);
        assertTrue(effect.contains("if (tick >= 10 || !plugin.isEnabled()"));
        assertTrue(effect.contains("!target.isValid() || target.isDead()"));
        assertTrue(effect.contains("!target.isOnline()"));
        assertTrue(effect.contains("!killed && target.isDead()"));
        assertTrue(effect.contains("!caster.getWorld().equals(target.getWorld())"));
        assertTrue(effect.contains("tick > CUTSCENE_TICKS"));
        assertEquals(7, count(effect, "finish(); return;"),
                "every execute abort path must deregister before returning");
        assertTrue(effect.contains("CameraShake.restore(caster)"));
        assertTrue(effect.contains("target.setVelocity(savedVelocity)"));
        assertTrue(effect.contains("target.setNoDamageTicks(savedNoDamageTicks)"));
    }

    @Test
    void disableFinishesCutscenesBeforeTheTaskSweep() throws IOException {
        String core = source(CORE);
        int from = core.indexOf("public void onDisable() {");
        int to = core.indexOf("private void runSafely(", from);
        assertTrue(from >= 0 && to > from);
        String disable = core.substring(from, to);
        int hook = disable.indexOf("ExecutionerSwordEffect.shutdown()");
        int sweep = disable.indexOf("Bukkit.getScheduler().cancelTasks(this)");
        assertTrue(hook > 0, "onDisable must finish live cutscenes explicitly");
        assertTrue(sweep > hook, "shutdown must run before cancelTasks");
        assertTrue(disable.contains("runSafely(\"executioner cutscenes\""));

        String effect = source(EFFECT);
        assertTrue(effect.contains("public static void shutdown()"));
        assertTrue(effect.contains("new ArrayList<>(ACTIVE)"), "shutdown must walk a stable copy");
        assertTrue(effect.contains("ACTIVE.clear()"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shutdownRestoresAFrozenTargetOnceAndIsSafeToRepeat() throws Exception {
        ServerMock server = MockBukkit.mock();
        PlayerMock caster = server.addPlayer();
        PlayerMock target = server.addPlayer();
        Vector savedVelocity = new Vector(1.0, 2.0, 3.0);
        int savedNoDamageTicks = 40;

        Constructor<?> constructor = Class.forName(
                "com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword."
                        + "ExecutionerSwordEffect$ExecuteTimeline")
                .getDeclaredConstructor(ShenanigansCore.class, Player.class, Player.class,
                        Vector.class, int.class, boolean.class);
        constructor.setAccessible(true);
        Object timeline = constructor.newInstance(null, caster, target, savedVelocity,
                savedNoDamageTicks, false);

        Field activeField = ExecutionerSwordEffect.class.getDeclaredField("ACTIVE");
        activeField.setAccessible(true);
        Set<Object> active = (Set<Object>) activeField.get(null);
        assertTrue(active.contains(timeline), "a live timeline must be owned by the effect");

        target.setVelocity(new Vector(9.0, 9.0, 9.0));
        target.setNoDamageTicks(0);
        ExecutionerSwordEffect.shutdown();
        assertTrue(active.isEmpty(), "shutdown must deregister every timeline");
        assertEquals(savedVelocity, target.getVelocity());
        assertEquals(savedNoDamageTicks, target.getNoDamageTicks());

        ExecutionerSwordEffect.shutdown();
        assertTrue(active.isEmpty());
        assertEquals(savedVelocity, target.getVelocity(), "a repeated shutdown must be a no-op");
    }
}
