package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.RoundState;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin Bukkit display driver for the Executioner's Sword: a heavy uppercut slash and the
 * 24-tick "Final Verdict" execution cutscene. Mirrors {@code DashSwordEffect}.
 */
public final class ExecutionerSwordEffect {

    public static final int CUTSCENE_TICKS = 24;

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private ExecutionerSwordEffect() { }

    // ------------------------------------------------------------------ uppercut

    public static void uppercut(Player caster, LivingEntity target) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (caster == null || target == null || plugin == null) return;

        // Cleave-style i-frame reset.
        target.setNoDamageTicks(0);
        SoundUtils.play(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);
        target.damage(12.0, caster);
        target.setVelocity(new Vector(0, 0.55, 0));
        // Launch sound plays at the launch moment, not the hit tick.
        SoundUtils.play(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 0.6f);

        new UppercutTimeline(plugin, caster, target).runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ execute

    public static void execute(Player caster, Player target) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (caster == null || target == null || plugin == null) return;

        Vector savedVelocity = target.getVelocity().clone();
        int savedNoDamageTicks = target.getNoDamageTicks();

        target.setNoDamageTicks(0);
        if (target.getHealth() > 0.5) {
            double mark = Math.min(0.5, target.getHealth() - 0.1);
            target.damage(mark, caster);
        }
        target.setNoDamageTicks(100);

        boolean roundActiveAtStart = false;
        BattlegroundManager manager = plugin.getBattlegroundManager();
        if (manager != null) {
            RoundState st = manager.state();
            roundActiveAtStart = (st == RoundState.RUNNING || st == RoundState.WARNING)
                    && manager.isDeployed(caster.getUniqueId());
        }

        new ExecuteTimeline(plugin, caster, target, savedVelocity, savedNoDamageTicks,
                roundActiveAtStart).runTaskTimer(plugin, 0L, 1L);
    }

    // ================================================================== uppercut timeline

    private static final class UppercutTimeline extends BukkitRunnable {
        private final ShenanigansCore plugin;
        private final Player caster;
        private final LivingEntity target;

        private ItemDisplay blade;
        private BlockDisplay ring;

        private int tick;

        UppercutTimeline(ShenanigansCore plugin, Player caster, LivingEntity target) {
            this.plugin = plugin;
            this.caster = caster;
            this.target = target;

            Location base = target.getLocation().clone();
            Vector forward = base.getDirection().clone();
            forward.setY(0);
            if (forward.lengthSquared() < 1.0e-6) forward = new Vector(0, 0, 1);
            forward.normalize();

            Color gold = Color.fromRGB(255, 215, 0);

            DisplayUtils.Options bladeOpts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(gold)
                    .glowing(true)
                    .build();
            Location bladeLoc = base.clone().add(forward.clone().multiply(0.6)).add(0, 0.6, 0);
            blade = DisplayUtils.spawnItem(bladeLoc, new ItemStack(Material.NETHERITE_SWORD), bladeOpts);

            DisplayUtils.Options ringOpts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(gold)
                    .glowing(true)
                    .build();
            Location ringLoc = base.clone().add(0, 0.05, 0);
            ring = DisplayUtils.spawnBlock(ringLoc, Material.GOLD_BLOCK, ringOpts);
        }

        @Override
        public void run() {
            if (tick >= 10 || !plugin.isEnabled()
                    || !caster.isOnline() || caster.isDead()
                    || !target.isValid() || target.isDead()
                    || !caster.getWorld().equals(target.getWorld())) {
                cleanup();
                cancel();
                return;
            }

            Location base = target.getLocation().clone();
            Vector forward = base.getDirection().clone();
            forward.setY(0);
            if (forward.lengthSquared() < 1.0e-6) forward = new Vector(0, 0, 1);
            forward.normalize();

            // Blade sweeps upward across target's front.
            Location bladeLoc = base.clone()
                    .add(forward.clone().multiply(0.6))
                    .add(0, 0.6 + tick * 0.12, 0);
            if (blade != null && blade.isValid()) {
                blade.teleport(bladeLoc);
                TransformUtils.applyFacing(blade,
                        new Vector3f(2.5f, 2.5f, 2.5f),
                        new Vector3f(0.3f, 1.0f, 0.0f),
                        new Vector3f(0.5f, 0.5f, 0.5f), 1);
            }

            // Ring expands 0.6 -> 1.4 over 6 ticks then fades.
            Location ringLoc = base.clone().add(0, 0.05, 0);
            if (ring != null && ring.isValid()) {
                ring.teleport(ringLoc);
                float s;
                if (tick < 6) {
                    s = 0.6f + (0.8f) * (tick / 6.0f);
                } else {
                    float fade = 1.0f - (tick - 6) / 4.0f;
                    if (fade < 0.02f) fade = 0.02f;
                    s = 1.4f * fade;
                    if (s < 0.02f) s = 0.02f;
                }
                TransformUtils.applyTransform(ring, s, 0.05f, s, 0f, 0f, 0f, 1);
            }

            tick++;
        }

        private void cleanup() {
            if (blade != null && blade.isValid()) blade.remove();
            if (ring != null && ring.isValid()) ring.remove();
            blade = null;
            ring = null;
        }
    }

    // ================================================================== execute timeline

    private static final class ExecuteTimeline extends BukkitRunnable {
        private final ShenanigansCore plugin;
        private final Player caster;
        private final Player target;
        private final Vector savedVelocity;
        private final int savedNoDamageTicks;
        private final boolean roundActiveAtStart;

        private final List<Entity> all = new ArrayList<>();
        private final List<BlockDisplay> plates = new ArrayList<>();
        private final List<ItemDisplay> ghosts = new ArrayList<>();
        private final List<ItemDisplay> shards = new ArrayList<>();

        private ItemDisplay guillotine;
        private TextDisplay verdictText;

        private Location anchor;
        private boolean killed = false;
        private int tick;

        ExecuteTimeline(ShenanigansCore plugin, Player caster, Player target, Vector savedVelocity,
                        int savedNoDamageTicks, boolean roundActiveAtStart) {
            this.plugin = plugin;
            this.caster = caster;
            this.target = target;
            this.savedVelocity = savedVelocity;
            this.savedNoDamageTicks = savedNoDamageTicks;
            this.roundActiveAtStart = roundActiveAtStart;
            this.anchor = target.getLocation().clone();
        }

        @Override
        public void run() {
            // abort guards
            if (!plugin.isEnabled()) { cleanup(); cancel(); return; }
            if (!caster.isOnline() || caster.isDead()) { cleanup(); cancel(); return; }
            if (!target.isOnline()) { cleanup(); cancel(); return; }
            if (!killed && target.isDead()) { cleanup(); cancel(); return; }
            if (!caster.getWorld().equals(target.getWorld())) { cleanup(); cancel(); return; }
            if (roundActiveAtStart) {
                BattlegroundManager mgr = plugin.getBattlegroundManager();
                if (mgr == null) { cleanup(); cancel(); return; }
                RoundState st = mgr.state();
                if (st != RoundState.RUNNING && st != RoundState.WARNING) { cleanup(); cancel(); return; }
            }

            if (target.isOnline()) anchor = target.getLocation().clone();

            // freeze target; root caster first 6 ticks
            if (target.isOnline()) target.setVelocity(new Vector(0, 0, 0));
            if (tick < 6 && caster.isOnline()) caster.setVelocity(new Vector(0, 0, 0));

            // spawns
            if (tick == 0) spawnPlates();
            if (tick == 4) spawnGhosts();
            if (tick == 12) spawnGuillotine();
            if (tick == 14) {
                spawnVerdictText();
                SoundUtils.play(anchor.clone(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
            }
            if (tick == 16) {
                resolveKill();
                spawnShards();
                SoundUtils.play(anchor.clone(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);
            }
            if (tick == 18) {
                SoundUtils.play(anchor.clone(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.9f);
            }

            // animate
            animatePlates();
            animateGhosts();
            animateGuillotine();
            animateVerdictText();
            animateShards();

            tick++;
            if (tick > CUTSCENE_TICKS) {
                cleanup();
                cancel();
            }
        }

        private void spawnPlates() {
            Color crimson = Color.fromRGB(139, 0, 0);
            DisplayUtils.Options opts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(crimson)
                    .glowing(true)
                    .build();
            for (int i = 0; i < 8; i++) {
                double angle = (Math.PI * 2.0 * i) / 8.0;
                Location loc = anchor.clone().add(Math.cos(angle) * 1.6, 0.05, Math.sin(angle) * 1.6);
                BlockDisplay bd;
                try {
                    bd = DisplayUtils.spawnBlock(loc, Material.CRIMSON_ROOTS, opts);
                } catch (IllegalArgumentException e) {
                    bd = DisplayUtils.spawnBlock(loc, Material.BLACKSTONE, opts);
                }
                plates.add(bd);
                all.add(bd);
                TransformUtils.applyTransform(bd, 1.0f, 0.01f, 1.0f, 0f, 0f, 0f, 1);
            }
        }

        private void spawnGhosts() {
            Color red = Color.RED;
            DisplayUtils.Options opts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(red)
                    .glowing(true)
                    .build();
            for (int i = 0; i < 3; i++) {
                double angle = (Math.PI * 2.0 * i) / 3.0;
                double radius = 2.0;
                Location loc = anchor.clone().add(Math.cos(angle) * radius, 1.0, Math.sin(angle) * radius);
                ItemDisplay id = DisplayUtils.spawnItem(loc, new ItemStack(Material.NETHERITE_SWORD), opts);
                ghosts.add(id);
                all.add(id);
                TransformUtils.applyTransform(id, 2.5f, 2.5f, 2.5f, 0f, 0f, 0f, 1);
            }
        }

        private void spawnGuillotine() {
            Color red = Color.RED;
            DisplayUtils.Options opts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(red)
                    .glowing(true)
                    .build();
            Location loc = anchor.clone().add(0, 3.0, 0);
            guillotine = DisplayUtils.spawnItem(loc, new ItemStack(Material.NETHERITE_SWORD), opts);
            all.add(guillotine);
            TransformUtils.applyFacing(guillotine,
                    new Vector3f(3.0f, 3.0f, 3.0f),
                    new Vector3f(0.0f, -1.0f, 0.0f),
                    new Vector3f(0.5f, 0.5f, 0.5f), 1);
        }

        private void spawnVerdictText() {
            Color darkRed = Color.fromRGB(139, 0, 0);
            DisplayUtils.Options opts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .billboard(org.bukkit.entity.Display.Billboard.CENTER)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(darkRed)
                    .glowing(true)
                    .build();
            Component text = MM.deserialize("<dark_red><bold>VERDICT");
            Location loc = anchor.clone().add(0, 2.5, 0);
            verdictText = DisplayUtils.spawnText(loc, text, opts);
            all.add(verdictText);
        }

        private void spawnShards() {
            Color red = Color.RED;
            DisplayUtils.Options opts = DisplayUtils.Options.builder()
                    .viewRange(12.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(red)
                    .glowing(true)
                    .build();
            for (int i = 0; i < 8; i++) {
                Location loc = anchor.clone().add(0, 1.0, 0);
                ItemDisplay sh = DisplayUtils.spawnItem(loc, new ItemStack(Material.NETHERITE_SWORD), opts);
                shards.add(sh);
                all.add(sh);
                TransformUtils.applyTransform(sh, 0.4f, 0.4f, 0.4f, 0f, 0f, 0f, 1);
            }
        }

        private void resolveKill() {
            killed = true;
            if (target.isOnline()) {
                target.setNoDamageTicks(0);
                double dmg = target.getHealth() + target.getAbsorptionAmount() + 10.0;
                target.damage(dmg, caster);
                if (target.isOnline() && !target.isDead() && target.getHealth() > 0.0) {
                    target.setHealth(0.0);
                    BattlegroundManager m = plugin.getBattlegroundManager();
                    if (m != null && m.isDeployed(caster.getUniqueId()) && m.isDeployed(target.getUniqueId())) {
                        m.combat().record(target.getUniqueId(), caster.getUniqueId(), 999.0);
                    }
                }
            }
        }

        private double plateY(int t) {
            if (t <= 16) return 0.05;
            if (t <= 20) return 0.05 + (-0.35) * (t - 16) / 4.0;
            return -0.3;
        }

        private float plateScaleY(int t) {
            if (t <= 3) return 0.01f + 0.04f * (t / 3.0f);
            if (t >= 20) {
                float fade = 1.0f - (t - 20) / 4.0f;
                if (fade < 0.0f) fade = 0.0f;
                return 0.05f * fade + 0.01f;
            }
            return 0.05f;
        }

        private double ghostRadius(int t) {
            if (t < 4) return 2.0;
            if (t <= 11) return lerp(2.0, 1.2, (t - 4) / 7.0);
            if (t <= 13) return lerp(1.2, 0.6, (t - 12) / 1.0);
            if (t <= 16) return 0.6;
            if (t <= 18) return lerp(0.6, 0.0, (t - 16) / 2.0);
            return 0.0;
        }

        private double guillotineY(int t) {
            if (t < 12) return 3.0;
            if (t <= 13) return lerp(3.0, 2.2, (t - 12) / 1.0);
            if (t <= 16) return 2.2;
            if (t <= 18) return lerp(2.2, 1.0, (t - 16) / 2.0);
            return 1.0;
        }

        private double shardRadius(int t) {
            if (t < 16) return 0.0;
            if (t <= 20) return 2.5 * (t - 16) / 4.0;
            return 2.5;
        }

        private float shardScale(int t) {
            if (t < 16) return 0.4f;
            if (t <= 20) return 0.4f - 0.35f * (t - 16) / 4.0f;
            float f = 1.0f - (t - 20) / 4.0f;
            if (f < 0.0f) f = 0.0f;
            return 0.05f * f;
        }

        private void animatePlates() {
            for (int i = 0; i < plates.size(); i++) {
                BlockDisplay bd = plates.get(i);
                if (bd == null || !bd.isValid()) continue;
                double angle = (Math.PI * 2.0 * i) / 8.0;
                double y = plateY(tick);
                Location loc = anchor.clone().add(Math.cos(angle) * 1.6, y, Math.sin(angle) * 1.6);
                bd.teleport(loc);
                float sy = plateScaleY(tick);
                TransformUtils.applyTransform(bd, 1.0f, sy, 1.0f, 0f, 0f, 0f, 1);
            }
        }

        private void animateGhosts() {
            double r = ghostRadius(tick);
            float sc;
            if (tick < 20) {
                sc = 2.5f;
            } else {
                float fade = 1.0f - (tick - 20) / 4.0f;
                if (fade < 0.01f) fade = 0.01f;
                sc = 2.5f * fade;
                if (sc < 0.01f) sc = 0.01f;
            }
            for (int i = 0; i < ghosts.size(); i++) {
                ItemDisplay id = ghosts.get(i);
                if (id == null || !id.isValid()) continue;
                double base = (Math.PI * 2.0 * i) / 3.0;
                double angle = base + tick * 0.3;
                Location loc = anchor.clone().add(Math.cos(angle) * r, 1.0, Math.sin(angle) * r);
                id.teleport(loc);
                TransformUtils.applyTransform(id, sc, sc, sc, 0f, 0f, 0f, 1);
            }
        }

        private void animateGuillotine() {
            if (guillotine == null || !guillotine.isValid()) return;
            double y = guillotineY(tick);
            Location loc = anchor.clone().add(0, y, 0);
            guillotine.teleport(loc);
            float sc;
            if (tick < 20) {
                sc = 3.0f;
            } else {
                float fade = 1.0f - (tick - 20) / 4.0f;
                if (fade < 0.01f) fade = 0.01f;
                sc = 3.0f * fade;
                if (sc < 0.01f) sc = 0.01f;
            }
            TransformUtils.applyFacing(guillotine,
                    new Vector3f(sc, sc, sc),
                    new Vector3f(0.0f, -1.0f, 0.0f),
                    new Vector3f(0.5f, 0.5f, 0.5f), 1);
        }

        private void animateVerdictText() {
            if (verdictText == null || !verdictText.isValid()) return;
            Location loc = anchor.clone().add(0, 2.5, 0);
            verdictText.teleport(loc);
            float sc;
            if (tick <= 16) sc = 1.0f;
            else if (tick <= 24) sc = 1.0f - (tick - 16) / 8.0f;
            else sc = 0.0f;
            if (sc < 0.01f) sc = 0.01f;
            TransformUtils.applyTransform(verdictText, sc, sc, sc, 0f, 0f, 0f, 1);
        }

        private void animateShards() {
            double r = shardRadius(tick);
            float sc = shardScale(tick);
            for (int i = 0; i < shards.size(); i++) {
                ItemDisplay sh = shards.get(i);
                if (sh == null || !sh.isValid()) continue;
                double angle = (Math.PI * 2.0 * i) / 8.0;
                Location loc = anchor.clone().add(Math.cos(angle) * r, 1.0, Math.sin(angle) * r);
                sh.teleport(loc);
                float s = sc;
                if (s < 0.01f) s = 0.01f;
                TransformUtils.applyTransform(sh, s, s, s, 0f, 0f, 0f, 1);
            }
        }

        private void cleanup() {
            for (Entity e : all) {
                if (e != null && e.isValid()) e.remove();
            }
            all.clear();
            plates.clear();
            ghosts.clear();
            shards.clear();
            guillotine = null;
            verdictText = null;

            // restore target
            if (target.isOnline()) {
                target.setVelocity(savedVelocity);
                target.setNoDamageTicks(savedNoDamageTicks);
            }
        }

        private static double lerp(double a, double b, double t) {
            if (t < 0) t = 0;
            if (t > 1) t = 1;
            return a + (b - a) * t;
        }
    }
}
