package com.hizeric.shenaniganscore.battleground.shop;

import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global shop policy read from {@code battleground/config.yml}. Kept separate from
 * {@code shop.yml} (which remains catalog data managed by the arena) so a policy toggle can be
 * reloaded without touching purchases or persisted player state.
 */
public final class ShopPolicyConfig {

    private static final ShopPolicyConfig EMPTY = new ShopPolicyConfig(false);
    private static volatile ShopPolicyConfig current = EMPTY;

    private final boolean killstreakRequirements;

    private ShopPolicyConfig(boolean killstreakRequirements) {
        this.killstreakRequirements = killstreakRequirements;
    }

    /** The shipped policy: killstreak requirements are off. */
    public static ShopPolicyConfig defaults() {
        return EMPTY;
    }

    /** The policy installed by the most recent {@link #load}, never null. */
    public static ShopPolicyConfig current() {
        return current;
    }

    public boolean killstreakRequirements() {
        return killstreakRequirements;
    }

    /** Pure seam: installs a policy without touching the filesystem. Used by load and tests. */
    public static ShopPolicyConfig apply(boolean killstreakRequirements) {
        ShopPolicyConfig policy = new ShopPolicyConfig(killstreakRequirements);
        current = policy;
        return policy;
    }

    /** Reads, installs, and returns the policy for {@code file}; bad input yields {@link #defaults()}. */
    public static ShopPolicyConfig load(File file, Logger logger) {
        boolean enabled = false;
        if (file == null || !file.isFile()) {
            if (logger != null) {
                logger.warning("Shop policy: " + (file == null ? "no config file" : file.getName())
                        + " not found; using shipped defaults");
            }
        } else {
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                CommentedConfigurationNode root = YamlConfigurationLoader.builder()
                        .source(() -> new BufferedReader(
                                new StringReader(new String(bytes, StandardCharsets.UTF_8))))
                        .nodeStyle(NodeStyle.BLOCK)
                        .build()
                        .load();
                ConfigurationNode node = root.node("shop", "killstreak-requirements");
                if (node.raw() instanceof Boolean value) {
                    enabled = value;
                } else if (logger != null) {
                    logger.warning("Shop policy: invalid shop.killstreak-requirements value '"
                            + node.raw() + "'; using the shipped default (false)");
                }
            } catch (IOException | RuntimeException e) {
                if (logger != null) {
                    logger.log(Level.WARNING,
                            "Shop policy: could not read " + file.getName() + "; using shipped defaults", e);
                }
                enabled = false;
            }
        }
        return apply(enabled);
    }
}
