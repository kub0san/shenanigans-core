package com.hizeric.shenaniganscore.core.config;

import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Global config auto-updater. Exactly two files are managed; runtime data
 * ({@code battleground/shop.yml}, {@code arena.yml}, {@code players.yml}, {@code globalkit.dat})
 * is never touched. Every gameplay value is hardcoded in per-module constants, so both YAMLs
 * are version stubs and {@code obsoleteKeys} enumerates the full legacy key set for cleanup.
 * Live arena/round keys are explicitly allow-listed and are never stripped as obsolete.
 *
 * <p>Runs on enable, before anything reads a gameplay value, and on {@code /bg reload}.
 * One broken file cannot disable the plugin.</p>
 */
public final class ConfigAutoUpdater {

    private static final int VERSION = 1;

    private static final java.util.List<String> CONFIG_YML_KEYS = java.util.List.of(
            "jujutsukaisen.malevolentshrine.radius",
            "jujutsukaisen.malevolentshrine.min-radius",
            "jujutsukaisen.malevolentshrine.max-radius",
            "jujutsukaisen.malevolentshrine.cooldown-seconds",
            "jujutsukaisen.malevolentshrine.caster-buffs",
            "jujutsukaisen.malevolentshrine.ground-thump",
            "jujutsukaisen.malevolentshrine.rumble",
            "jujutsukaisen.malevolentshrine.rumble-intensity",
            "jujutsukaisen.malevolentshrine.high-detail",
            "jujutsukaisen.malevolentshrine.capture.include-caster",
            "jujutsukaisen.malevolentshrine.underlevel.mode",
            "jujutsukaisen.malevolentshrine.underlevel.rise-ticks",
            "jujutsukaisen.malevolentshrine.underlevel.glide-ticks",
            "jujutsukaisen.malevolentshrine.return.mode",
            "jujutsukaisen.malevolentshrine.return.duration",
            "jujutsukaisen.malevolentshrine.return.easing",
            "jujutsukaisen.malevolentshrine.blocks.indestructible",
            "jujutsukaisen.malevolentshrine.timeline.sign-seconds",
            "jujutsukaisen.malevolentshrine.timeline.awaken-seconds",
            "jujutsukaisen.malevolentshrine.timeline.domain-seconds",
            "jujutsukaisen.malevolentshrine.timeline.collapse-seconds",
            "jujutsukaisen.malevolentshrine.timeline.wave-gap-seconds",
            "jujutsukaisen.malevolentshrine.cleave.percent",
            "jujutsukaisen.malevolentshrine.cleave.interval-seconds",
            "jujutsukaisen.malevolentshrine.dismantle.shred-percent",
            "jujutsukaisen.malevolentshrine.dismantle.wave-shred-percent",
            "jujutsukaisen.malevolentshrine.dismantle.can-destroy-armor",
            "jujutsukaisen.malevolentshrine.dismantle.shield-cooldown-ticks",
            "jujutsukaisen.malevolentshrine.dismantle.wave-speed",
            "jujutsukaisen.malevolentshrine.dismantle.wave-lifetime-ticks",
            "jujutsukaisen.malevolentshrine.dismantle.max-live-waves",
            "jujutsukaisen.malevolentshrine.dismantle.projectile-cut-delay-ticks",
            "jujutsukaisen.malevolentshrine.floor.max-radius",
            "jujutsukaisen.malevolentshrine.floor.blocks-per-tick",
            "jujutsukaisen.unlimitedvoid.radius",
            "jujutsukaisen.unlimitedvoid.min-radius",
            "jujutsukaisen.unlimitedvoid.max-radius",
            "jujutsukaisen.unlimitedvoid.cooldown-seconds",
            "jujutsukaisen.unlimitedvoid.caster-buffs",
            "jujutsukaisen.unlimitedvoid.quality",
            "jujutsukaisen.unlimitedvoid.capture.include-caster",
            "jujutsukaisen.unlimitedvoid.underlevel.mode",
            "jujutsukaisen.unlimitedvoid.underlevel.rise-ticks",
            "jujutsukaisen.unlimitedvoid.underlevel.glide-ticks",
            "jujutsukaisen.unlimitedvoid.return.mode",
            "jujutsukaisen.unlimitedvoid.return.duration",
            "jujutsukaisen.unlimitedvoid.return.easing",
            "jujutsukaisen.unlimitedvoid.overlay.blackout-lip",
            "jujutsukaisen.unlimitedvoid.blocks.indestructible",
            "jujutsukaisen.unlimitedvoid.stars",
            "jujutsukaisen.unlimitedvoid.domain-seconds",
            "jujutsukaisen.unlimitedvoid.effect-seconds",
            "jujutsukaisen.unlimitedvoid.onset-seconds",
            "jujutsukaisen.unlimitedvoid.deep-seconds",
            "jujutsukaisen.unlimitedvoid.psychic-damage",
            "jujutsukaisen.unlimitedvoid.floor.max-radius",
            "jujutsukaisen.unlimitedvoid.floor.blocks-per-tick",
            "jujutsukaisen.unlimitedvoid.floor.block",
            "jujutsukaisen.unlimitedvoid.limitless.max-halos",
            "jujutsukaisen.timecellmoonpalace.radius",
            "jujutsukaisen.timecellmoonpalace.max-radius",
            "jujutsukaisen.timecellmoonpalace.cooldown-seconds",
            "jujutsukaisen.timecellmoonpalace.caster-buffs",
            "jujutsukaisen.timecellmoonpalace.barrier-lock",
            "jujutsukaisen.timecellmoonpalace.capture.include-caster",
            "jujutsukaisen.timecellmoonpalace.underlevel.mode",
            "jujutsukaisen.timecellmoonpalace.underlevel.rise-ticks",
            "jujutsukaisen.timecellmoonpalace.underlevel.glide-ticks",
            "jujutsukaisen.timecellmoonpalace.return.mode",
            "jujutsukaisen.timecellmoonpalace.return.duration",
            "jujutsukaisen.timecellmoonpalace.return.easing",
            "jujutsukaisen.timecellmoonpalace.overlay.blackout-lip",
            "jujutsukaisen.timecellmoonpalace.blocks.indestructible",
            "jujutsukaisen.timecellmoonpalace.floor.max-radius",
            "jujutsukaisen.timecellmoonpalace.floor.blocks-per-tick",
            "jujutsukaisen.timecellmoonpalace.floor.block",
            "jujutsukaisen.timecellmoonpalace.cinematics.cut-to-black",
            "jujutsukaisen.timecellmoonpalace.cinematics.projector-beam",
            "jujutsukaisen.timecellmoonpalace.timeline.sign-seconds",
            "jujutsukaisen.timecellmoonpalace.timeline.expand-seconds",
            "jujutsukaisen.timecellmoonpalace.timeline.domain-seconds",
            "jujutsukaisen.timecellmoonpalace.timeline.collapse-seconds",
            "jujutsukaisen.timecellmoonpalace.rule.meter-per-block",
            "jujutsukaisen.timecellmoonpalace.rule.meter-decay-per-second",
            "jujutsukaisen.timecellmoonpalace.rule.bleed-per-block",
            "jujutsukaisen.timecellmoonpalace.rule.frozen-bleed-per-second",
            "jujutsukaisen.timecellmoonpalace.rule.freeze-seconds",
            "jujutsukaisen.timecellmoonpalace.rule.grace-seconds",
            "jujutsukaisen.timecellmoonpalace.rule.swing-meter",
            "jujutsukaisen.timecellmoonpalace.rule.swing-bleed",
            "jujutsukaisen.timecellmoonpalace.rule.sever-percent",
            "jujutsukaisen.timecellmoonpalace.rule.sever-cooldown-seconds",
            "jujutsukaisen.timecellmoonpalace.rule.sever-drops-item",
            "jujutsukaisen.timecellmoonpalace.rule.stutter-every-frames",
            "jujutsukaisen.techniques.fuga.cooldown-seconds",
            "jujutsukaisen.techniques.red.cooldown-seconds",
            "jujutsukaisen.techniques.piercingblood.cooldown-seconds",
            "jujutsukaisen.techniques.purple.cooldown-seconds",
            "jujutsukaisen.techniques.blue.cooldown-seconds",
            "minecraft.quality",
            "minecraft.dash.cooldown-seconds",
            "minecraft.dash.distance",
            "minecraft.executioner.cooldown-seconds",
            "minecraft.executioner.execute-threshold",
            "minecraft.items.idle-orbit"
    );

private static final java.util.List<String> BATTLEGROUND_KEYS = java.util.List.of(
            "enabled",
            "economy.kill",
            "economy.assist",
            "economy.assist-window-seconds",
            "economy.assist-min-damage-percent",
            "economy.first-blood-bonus",
            "economy.streak.3",
            "economy.streak.5",
            "economy.streak.7",
            "economy.bounty.enabled",
            "economy.bounty.bonus-percent",
            "economy.anti-farm.enabled",
            "economy.anti-farm.window-seconds",
            "economy.anti-farm.second-kill-value",
            "economy.anti-farm.third-kill-value",
            "economy.free-pick.enabled",
            "economy.free-rotation.enabled",
            "combat.spawn-protection-seconds",
            "combat.last-damager-window-seconds",
            "combat.leave-region",
            "combat.command-policy.enabled",
            "combat.command-policy.mode",
            "combat.command-policy.allowed-roots",
            "combat.command-policy.blocked-roots",
            "loadout.editable",
            "arena.restore.defer-near-player"
    );

    /** Live arena/round keys read by {@code ArenaRuntimeConfig}; never stripped from disk. */
    public static final java.util.Set<String> BATTLEGROUND_ALLOWED_KEYS = java.util.Set.of(
            "arena.round.duration-seconds",
            "arena.round.warning-seconds",
            "arena.round.pause-when-empty",
            "arena.protection.enabled",
            "arena.restore.relight",
            "arena.restore.verify",
            "arena.restore.fallback-on-unverified",
            "arena.restore.ms-per-tick",
            "arena.restore.blocks-per-tick",
            "arena.restore.chunks-per-tick",
            "arena.restore.animation.enabled",
            "arena.restore.animation.particles-per-chunk",
            "arena.restore.animation.sounds",
            "arena.restore.animation.action-bar");

    private static List<String> battlegroundObsoleteKeys() {
        return BATTLEGROUND_KEYS.stream()
                .filter(key -> !BATTLEGROUND_ALLOWED_KEYS.contains(key))
                .toList();
    }

    private static final List<ConfigSpec> BUNDLED_CONFIGS = List.of(
            new ConfigSpec("config.yml", CONFIG_YML_KEYS, VERSION),
            new ConfigSpec("battleground/config.yml", battlegroundObsoleteKeys(), VERSION));

    private final File dataFolder;
    private final Function<String, InputStream> resources;
    private final Logger logger;

    public ConfigAutoUpdater(Plugin plugin) {
        this(plugin.getDataFolder(), name -> plugin.getResource(name), plugin.getLogger());
    }

    /** Test entry point: no Bukkit plugin stub needed. */
    ConfigAutoUpdater(File dataFolder, Function<String, InputStream> resources, Logger logger) {
        this.dataFolder = dataFolder;
        this.resources = resources;
        this.logger = logger;
    }

    /** @return the number of files that were written */
    public int run() {
        int updated = 0;
        for (ConfigSpec spec : BUNDLED_CONFIGS) {
            try {
                if (update(spec)) updated++;
            } catch (Throwable t) {
                logger.severe("ConfigAutoUpdater: " + spec.resourcePath() + " failed: " + t.getMessage());
            }
        }
        if (updated > 0) {
            logger.info("ConfigAutoUpdater: " + updated + " config file(s) updated.");
        }
        return updated;
    }

    private boolean update(ConfigSpec spec) {
        byte[] bundled;
        try (InputStream in = resources.apply(spec.resourcePath())) {
            if (in == null) {
                logger.severe("ConfigAutoUpdater: bundled resource '" + spec.resourcePath()
                        + "' is missing; skipping it");
                return false;
            }
            bundled = in.readAllBytes();
        } catch (Exception e) {
            logger.severe("ConfigAutoUpdater: could not read bundled " + spec.resourcePath() + ": "
                    + e.getMessage());
            return false;
        }
        return ConfigFiles.updateFile(new File(dataFolder, spec.resourcePath()), bundled, spec, logger);
    }
}
