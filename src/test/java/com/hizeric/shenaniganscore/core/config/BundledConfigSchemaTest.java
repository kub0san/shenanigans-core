package com.hizeric.shenaniganscore.core.config;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledConfigSchemaTest {

    private static ConfigurationNode parse(String resource) throws Exception {
        try (InputStream in = BundledConfigSchemaTest.class.getResourceAsStream(resource)) {
            assertNotNull(in, "bundled resource " + resource + " must exist");
            return YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load();
        }
    }

    private static void collectKeys(ConfigurationNode node, List<String> keys) {
        for (Map.Entry<Object, ? extends ConfigurationNode> entry : node.childrenMap().entrySet()) {
            keys.add(String.valueOf(entry.getKey()));
            collectKeys(entry.getValue(), keys);
        }
    }

    @Test
    void bundledAbilitySchemaMatchesTheApprovedValues() throws Exception {
        ConfigurationNode root = parse("/config.yml");
        assertEquals(30, root.node("abilities", "items", "dash-sword", "cooldown").getInt());

        ConfigurationNode techniques = root.node("abilities", "techniques");
        assertEquals(30, techniques.node("blue", "cooldown").getInt());
        assertEquals(40, techniques.node("red", "cooldown").getInt());
        assertEquals(45, techniques.node("piercing-blood", "cooldown").getInt());
        assertEquals(60, techniques.node("purple", "cooldown").getInt());
        assertEquals(75, techniques.node("fuga", "cooldown").getInt());
        assertEquals(90, techniques.node("world-slash", "cooldown").getInt());

        assertEquals(2.0, techniques.node("blue", "damage").getDouble(), 0.0001);
        assertEquals(18.0, techniques.node("red", "damage").getDouble(), 0.0001);
        assertEquals(18.0, techniques.node("piercing-blood", "damage").getDouble(), 0.0001);
        assertEquals(24.0, techniques.node("purple", "damage").getDouble(), 0.0001);
        assertEquals(24.0, techniques.node("fuga", "damage").getDouble(), 0.0001);

        assertTrue(techniques.node("piercing-blood", "armor-piercing").getBoolean());
        assertTrue(techniques.node("purple", "armor-piercing").getBoolean());
        assertTrue(techniques.node("world-slash", "armor-piercing").getBoolean());
    }

    @Test
    void bundledShopPolicyDisablesKillstreakRequirements() throws Exception {
        ConfigurationNode root = parse("/battleground/config.yml");
        ConfigurationNode flag = root.node("shop", "killstreak-requirements");
        assertFalse(flag.virtual(), "the key must be present in the bundled file");
        assertFalse(flag.getBoolean());
    }

    @Test
    void bundledArenaConfigCarriesTheLiveDefaults() throws Exception {
        ConfigurationNode root = parse("/battleground/config.yml");
        assertEquals(1200, root.node("arena", "round", "duration-seconds").getInt());
        assertEquals(15, root.node("arena", "round", "warning-seconds").getInt());
        assertTrue(root.node("arena", "round", "pause-when-empty").getBoolean());
        assertTrue(root.node("arena", "protection", "enabled").getBoolean());
        assertTrue(root.node("arena", "restore", "relight").getBoolean());
        assertTrue(root.node("arena", "restore", "verify").getBoolean());
        assertTrue(root.node("arena", "restore", "fallback-on-unverified").getBoolean());
        assertEquals(30, root.node("arena", "restore", "ms-per-tick").getInt());
        assertEquals(256, root.node("arena", "restore", "blocks-per-tick").getInt());
        assertEquals(4, root.node("arena", "restore", "chunks-per-tick").getInt());
        assertTrue(root.node("arena", "restore", "defer-near-player").virtual(), "the retired key must not be in the bundled stub");
    }

    @Test
    void noBundledKeyUsesTheRejectedNamingSchemes() throws Exception {
        List<String> keys = new ArrayList<>();
        collectKeys(parse("/config.yml"), keys);
        collectKeys(parse("/battleground/config.yml"), keys);
        // The two arena round keys are mandated by the live-config design; every other
        // -seconds / minimum-cooldown name is rejected.
        List<String> mandated = List.of("duration-seconds", "warning-seconds");
        List<String> rejected = keys.stream()
                .filter(key -> key.contains("minimum-cooldown") || key.contains("-seconds"))
                .filter(key -> !mandated.contains(key))
                .toList();
        assertTrue(rejected.isEmpty(), "rejected key names found: " + rejected);
    }
}
