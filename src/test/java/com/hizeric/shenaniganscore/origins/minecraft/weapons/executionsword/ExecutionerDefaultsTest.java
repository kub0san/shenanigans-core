package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionerDefaultsTest {

    @TempDir
    Path folder;

    @AfterEach
    void restoreDefaults() {
        AbilityTuningConfig.load(null, null);
        MockBukkit.unmock();
    }

    private void load(String yaml) throws IOException {
        File target = new File(folder.toFile(), "config.yml");
        Files.writeString(target.toPath(), yaml, StandardCharsets.UTF_8);
        AbilityTuningConfig.load(target, Logger.getLogger("test"));
    }

    @Test
    void cooldownDefaultIsNinety() {
        assertEquals(90, ExecutionerSwordItem.DEFAULT_COOLDOWN_SECONDS);
    }

    @Test
    void executeThresholdIsSixHp() {
        assertEquals(6.0, ExecutionerSwordItem.EXECUTE_THRESHOLD, 1e-9);
    }

    @Test
    void armDurationIsSixSeconds() {
        assertEquals(6000L, ExecutionerSwordItem.ARM_DURATION_MS);
    }

    @Test
    void cutsceneLengthIsTwentyFourTicks() {
        assertEquals(24, ExecutionerSwordEffect.CUTSCENE_TICKS);
    }

    @Test
    void priceIsFiveHundred() {
        assertEquals(500, ExecutionerSwordItem.PRICE);
    }

    @Test
    void abilityIdIsNamespaced() {
        assertEquals("minecraft:executioners_sword", ExecutionerSwordItem.ABILITY_ID.toString());
    }

    @Test
    void everyLegacyIdStillResolvesToTheCanonicalItem() {
        assertEquals("executioner_sword", ExecutionerSwordId.OLD_ID);
        for (String legacy : ExecutionerSwordId.LEGACY_IDS) {
            assertEquals("minecraft:executioners_sword", ExecutionerSwordId.migrate(legacy));
        }
        assertEquals("minecraft:executioners_sword",
                ExecutionerSwordId.migrate("minecraft:executioners_sword"));
        assertEquals("jjk:blue", ExecutionerSwordId.migrate("jjk:blue"));
    }

    @Test
    void registeredDefinitionIsVisibleWithDiamondAxeIcon() {
        var definition = ExecutionerSwordAbility.definition(new ExecutionerSwordHitListener());
        assertEquals("minecraft:executioners_sword", definition.id().toString());
        assertEquals(Material.DIAMOND_AXE, definition.iconMaterial());
        assertEquals(false, definition.hidden());
    }

    @Test
    void displayNameAndLoreUseAxeWording() {
        assertEquals("Executioner's Axe", ExecutionerSwordItem.DISPLAY_NAME);
        String lore = String.join("\n", ExecutionerSwordItem.lore());
        assertTrue(lore.contains("Executioner's Axe"), lore);
        assertFalse(lore.contains("Sword"), lore);
    }

    @Test
    void builtItemIsANetheriteAxeNamedExecutionersAxe() {
        MockBukkit.mock();
        ItemStack built = ExecutionerSwordItem.build();
        assertEquals(Material.NETHERITE_AXE, built.getType());
        assertEquals(MiniMessage.miniMessage().deserialize("<dark_red><bold>Executioner's Axe"),
                built.getItemMeta().displayName());
        assertTrue(ExecutionerSwordItem.isExecutionerSword(built),
                "renaming the item must not change its persistent-data identity");
    }

    @Test
    void armingNoticeIsSubtitleOnlyAndNeverUsesTheActionBar() throws IOException {
        String source = Files.readString(Path.of("src", "main", "java", "com", "hizeric",
                "shenaniganscore", "origins", "minecraft", "weapons", "executionsword",
                "ExecutionerSwordHitListener.java"), StandardCharsets.UTF_8);
        assertFalse(source.contains("sendActionBar"),
                "the arming notice must not take the single action-bar slot");
        assertTrue(source.contains("player.showTitle(Title.title(Component.empty(),"),
                "the notice belongs in the subtitle, with no main title");
        assertTrue(source.contains(
                        "Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(200))"),
                "the subtitle must use the agreed fade timings");
    }

    @Test
    void shippedConfigSuppliesTheExecutionerCooldown() throws Exception {
        URL url = ExecutionerDefaultsTest.class.getResource("/config.yml");
        assertNotNull(url);
        AbilityTuningConfig.load(new File(url.toURI()), Logger.getLogger("test"));
        assertEquals(90, ExecutionerSwordItem.cooldownSeconds());
    }

    @Test
    void oldConfigKeyStillSuppliesCooldown() throws IOException {
        load("""
                abilities:
                  items:
                    executioners-sword:
                      cooldown: 42
                """);
        assertEquals(42, ExecutionerSwordItem.cooldownSeconds());
    }

    @Test
    void axeAliasSuppliesCooldownOnlyWhenTheOldKeyIsAbsent() throws IOException {
        load("""
                abilities:
                  items:
                    executioners-axe:
                      cooldown: 66
                """);
        assertEquals(66, ExecutionerSwordItem.cooldownSeconds());

        load("""
                abilities:
                  items:
                    executioners-sword:
                      cooldown: 42
                    executioners-axe:
                      cooldown: 66
                """);
        assertEquals(42, ExecutionerSwordItem.cooldownSeconds(),
                "the authoritative old key must not be silently overridden by the alias");
    }
}
