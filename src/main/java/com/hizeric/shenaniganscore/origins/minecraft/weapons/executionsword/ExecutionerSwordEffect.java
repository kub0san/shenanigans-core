package com.hizeric.shenaniganscore.origins.minecraft.weapons.executionsword;

import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.battleground.BattlegroundManager;
import com.hizeric.shenaniganscore.battleground.RoundState;
import com.hizeric.shenaniganscore.core.display.BlockDisplayOps;
import com.hizeric.shenaniganscore.core.display.CameraShake;
import com.hizeric.shenaniganscore.core.display.DisplayRig;
import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.LightingRig;
import com.hizeric.shenaniganscore.core.display.TechniqueDisplayOps;
import com.hizeric.shenaniganscore.core.display.TechniqueLighting;
import com.hizeric.shenaniganscore.core.display.TitleAnimator;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import com.hizeric.shenaniganscore.core.sound.SoundUtils;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Thin Bukkit display driver for the Executioner's Axe: a heavy uppercut slash and the
 * 24-tick "Final Verdict" execution cutscene. Mirrors {@code DashSwordEffect}.
 */
public final class ExecutionerSwordEffect {

    public static final int CUTSCENE_TICKS = 24;

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

    private static final BlockData BLADE_CORE =
            Material.WHITE_CONCRETE.createBlockData();
    private static final BlockData BLADE_GLOW =
            Material.RED_STAINED_GLASS.createBlockData();
    private static final Particle.DustOptions CRIMSON_DUST =
            new Particle.DustOptions(Color.fromRGB(150, 0, 12), 1.2f);

    private static final class UppercutTimeline extends BukkitRunnable {
        private final ShenanigansCore plugin;
        private final Player caster;
        private final LivingEntity target;

        // Two-layer main blade: white-hot core + wider red-glass glow.
        private BlockDisplay core;
        private BlockDisplay glow;
        // Cross accent at contact (two short blades).
        private final List<BlockDisplay> crossBlades = new ArrayList<>();

        private int tick;

        UppercutTimeline(ShenanigansCore plugin, Player caster, LivingEntity target) {
            this.plugin = plugin;
            this.caster = caster;
            this.target = target;

            // Anchor at the target's chest, pushed ~0.5 blocks along its facing.
            Location chest = chestAnchor();
            Vector forward = horizontalForward();

            DisplayUtils.Options coreOpts = DisplayUtils.Options.builder()
                    .viewRange(8.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(Color.WHITE)
                    .glowing(true)
                    .build();
            core = DisplayUtils.spawnBlock(chest, BLADE_CORE, coreOpts);

            DisplayUtils.Options glowOpts = DisplayUtils.Options.builder()
                    .viewRange(8.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(Color.fromRGB(200, 20, 20))
                    .glowing(true)
                    .build();
            glow = DisplayUtils.spawnBlock(chest, BLADE_GLOW, glowOpts);

            // Orient both layers down the target's forward: local +Z is the blade length.
            Vector3f initialDir = new Vector3f((float) forward.getX(), 0f, (float) forward.getZ());
            Vector3f pivot = new Vector3f(0.5f, 0.5f, 0.5f);
            TransformUtils.applyFacing(core, new Vector3f(0.05f, 0.05f, 1.4f), initialDir, pivot, 1);
            TransformUtils.applyFacing(glow, new Vector3f(0.25f, 0.25f, 1.4f), initialDir, pivot, 1);

            // Cross accent at contact: two short blades at base forward ±55°.
            Vector leftDir = rotateY(forward, 55.0);
            Vector rightDir = rotateY(forward, -55.0);
            spawnCrossBlade(chest, leftDir, coreOpts, glowOpts);
            spawnCrossBlade(chest, rightDir, coreOpts, glowOpts);
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

            World world = target.getWorld();
            Location chest = chestAnchor();
            Vector forward = horizontalForward();

            if (tick == 0) {
                // Camera kick once on hit.
                CameraShake.start(plugin, caster, 6, 1.2f);
            }

            // ── Blade sweep: -34° → +34° around the forward over ticks 0..4 ──
            if (tick <= 4) {
                double t = tick / 4.0;
                double angle = -34.0 + 68.0 * t;       // -34° → +34°
                double length = 1.4 + 0.6 * t;         // 1.4 → 2.0
                Vector dir = rotateY(forward, angle);
                poseLayer(core, chest, dir, length, 0.05f);
                poseLayer(glow, chest, dir, length, 0.25f);
            } else if (tick <= 6) {
                // Thin both layers toward 0.02 over 2 ticks, then they are removed.
                float thin = 0.05f - (0.05f - 0.02f) * (tick - 4) / 2.0f;
                Vector dir = rotateY(forward, 34.0);
                poseLayer(core, chest, dir, 2.0, thin);
                poseLayer(glow, chest, dir, 2.0, thin * 5.0f);
            }

            // ── Rising crimson dust helix for ticks 0..7 ──
            if (tick <= 7) {
                double y = chest.getY() - 0.9 + 0.2 + tick * 0.16; // relative to feet-ish origin
                for (int i = 0; i < 2; i++) {
                    double a = (tick * 0.7) + i * Math.PI;
                    double x = chest.getX() + Math.cos(a) * 0.55;
                    double z = chest.getZ() + Math.sin(a) * 0.55;
                    world.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, CRIMSON_DUST);
                }
            }

            // ── Cross accent: shrink away just after the sweep ──
            if (tick == 4) {
                for (BlockDisplay bd : crossBlades) BlockDisplayOps.shrink(bd, 2);
            }

            tick++;
        }

        // ---- helpers ----

        private Location chestAnchor() {
            return target.getLocation().clone().add(0, 0.9, 0);
        }

        private Vector horizontalForward() {
            Vector forward = target.getLocation().getDirection().clone();
            forward.setY(0);
            if (forward.lengthSquared() < 1.0e-6) forward = new Vector(0, 0, 1);
            forward.normalize();
            return forward;
        }

        /** Rotate a horizontal vector around the Y axis by {@code degrees}. */
        private static Vector rotateY(Vector v, double degrees) {
            double r = Math.toRadians(degrees);
            double cos = Math.cos(r);
            double sin = Math.sin(r);
            double x = v.getX() * cos - v.getZ() * sin;
            double z = v.getX() * sin + v.getZ() * cos;
            return new Vector(x, 0, z);
        }

        private void poseLayer(BlockDisplay display, Location chest, Vector dir,
                               double length, float thickness) {
            if (display == null || !display.isValid()) return;
            // Anchor at the target's chest, pushed ~0.5 blocks along its facing.
            Location anchor = chest.clone().add(dir.clone().multiply(0.5));
            display.teleport(anchor);
            TransformUtils.applyFacing(display,
                    new Vector3f(thickness, thickness, (float) length),
                    new Vector3f((float) dir.getX(), 0f, (float) dir.getZ()),
                    new Vector3f(0.5f, 0.5f, 0.5f), 1);
        }

        private void spawnCrossBlade(Location chest, Vector dir,
                                     DisplayUtils.Options coreOpts,
                                     DisplayUtils.Options glowOpts) {
            BlockDisplay c = DisplayUtils.spawnBlock(chest, BLADE_CORE, coreOpts);
            BlockDisplay g = DisplayUtils.spawnBlock(chest, BLADE_GLOW, glowOpts);
            crossBlades.add(c);
            crossBlades.add(g);
            poseLayer(c, chest, dir, 0.9, 0.05f);
            poseLayer(g, chest, dir, 0.9, 0.25f);
        }



        private void cleanup() {
            // The shake outlives the effect otherwise; restore it even on an early abort.
            CameraShake.restore(caster);
            if (core != null && core.isValid()) core.remove();
            if (glow != null && glow.isValid()) glow.remove();
            core = null;
            glow = null;
            for (BlockDisplay bd : crossBlades) {
                if (bd != null && bd.isValid()) bd.remove();
            }
            crossBlades.clear();
        }
    }

    // ================================================================== execute timeline

    private static final class ExecuteTimeline extends BukkitRunnable {
        private static final Vector3f PIVOT = new Vector3f(0.5f, 0.5f, 0.5f);
        private static final Color RED_GLOW = Color.fromRGB(200, 20, 20);
        private static final TextColor SEAL_TEXT = TextColor.color(139, 0, 0);
        private static final double RING_RADIUS = 1.9;
        private static final double RING_Y = 0.06;
        private static final int RING_SEGMENTS = 24;
        /** Cardinal unit offsets (x, z) for the four seal glyphs. */
        private static final double[][] CARDINALS = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};

        /** One entity + fake-light registry so cleanup is a single pass. */
        private static final class Rig implements DisplayRig, LightingRig {
            private final List<Entity> entities = new ArrayList<>();
            private final Map<Location, Integer> lights = new HashMap<>();

            @Override
            public List<Entity> entities() {
                return entities;
            }

            @Override
            public Map<Location, Integer> lights() {
                return lights;
            }
        }

        private final ShenanigansCore plugin;
        private final Player caster;
        private final Player target;
        private final Vector savedVelocity;
        private final int savedNoDamageTicks;
        private final boolean roundActiveAtStart;
        private final World world;

        private final Rig rig = new Rig();

        // Typed handles for per-beat animation (all also registered on the rig).
        private final List<BlockDisplay> ring = new ArrayList<>();
        private final List<TextDisplay> seals = new ArrayList<>();
        private final List<BlockDisplay> bladeCores = new ArrayList<>();
        private final List<BlockDisplay> bladeGlows = new ArrayList<>();
        private final List<BlockDisplay> shards = new ArrayList<>();
        private BlockDisplay cutCore;
        private BlockDisplay cutGlow;
        private boolean severed;

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
            this.world = target.getWorld();
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

            // ---- beats ----
            if (tick == 0) {
                spawnRing();
                lightBurst(2.0);
                SoundUtils.play(anchor.clone(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 0.5f);
            }
            if (tick == 2) {
                spawnSeals();
            }
            if (tick == 4) {
                spawnBlades();
            }
            if (tick == 12) {
                spawnCut();
                lightBurst(2.4);
                SoundUtils.play(anchor.clone(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.7f);
                CameraShake.start(plugin, caster, 5, 1.0f);
            }
            if (tick == 14) {
                resolveKill();
                SoundUtils.play(anchor.clone(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f);
                CameraShake.start(plugin, caster, 8, 1.8f);
            }
            if (tick == 16) {
                spawnShards();
                for (BlockDisplay c : bladeCores) discard(c);
                for (BlockDisplay g : bladeGlows) discard(g);
                bladeCores.clear();
                bladeGlows.clear();
                discard(cutCore);
                discard(cutGlow);
                cutCore = null;
                cutGlow = null;
                lightBurst(1.2);
                SoundUtils.play(anchor.clone(), Sound.BLOCK_ANVIL_LAND, 0.9f, 0.8f);
            }
            if (tick == 18) {
                SoundUtils.play(anchor.clone(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.9f);
            }
            if (tick == 20) {
                for (TextDisplay s : seals) discard(s);
                seals.clear();
            }

            // ---- animate ----
            animateRing();
            animateSeals();
            animateBlades();
            animateCut();
            if (tick >= 14 && tick <= 23) animateVerdict();
            animateShards();

            tick++;
            if (tick > CUTSCENE_TICKS) {
                cleanup();
                cancel();
            }
        }

        // ---- spawns ----

        private void spawnRing() {
            for (int i = 0; i < RING_SEGMENTS; i++) {
                boolean white = i % 3 == 0;
                Material mat = white ? Material.WHITE_CONCRETE : Material.RED_STAINED_GLASS;
                Color glow = white ? Color.WHITE : RED_GLOW;
                double base = (Math.PI * 2.0 * i) / RING_SEGMENTS;
                Location loc = anchor.clone().add(
                        Math.cos(base) * RING_RADIUS, RING_Y, Math.sin(base) * RING_RADIUS);
                BlockDisplay bd = DisplayUtils.spawnBlock(loc, mat, glowOpts(glow));
                rig.entities().add(bd);
                ring.add(bd);
                Vector3f radial = new Vector3f((float) Math.cos(base), 0f, (float) Math.sin(base));
                TransformUtils.applyFacing(bd, new Vector3f(0.05f, 0.03f, 0.5f), radial, PIVOT, 1);
            }
        }

        private void spawnSeals() {
            for (double[] dir : CARDINALS) {
                Location loc = anchor.clone().add(dir[0] * 2.2, 1.2, dir[1] * 2.2);
                TextDisplay td = TechniqueDisplayOps.spawnGlyph(rig, loc, "斬", SEAL_TEXT, 0.6f);
                seals.add(td);
            }
        }

        private void spawnBlades() {
            for (int i = 0; i < 3; i++) {
                double ang = (Math.PI * 2.0 * i) / 3.0;
                Location loc = anchor.clone().add(Math.cos(ang) * 2.4, 1.0, Math.sin(ang) * 2.4);
                BlockDisplay c = DisplayUtils.spawnBlock(loc, BLADE_CORE, glowOpts(Color.WHITE));
                BlockDisplay g = DisplayUtils.spawnBlock(loc, BLADE_GLOW, glowOpts(RED_GLOW));
                rig.entities().add(c);
                rig.entities().add(g);
                bladeCores.add(c);
                bladeGlows.add(g);
                Vector3f inward = new Vector3f((float) -Math.cos(ang), 0f, (float) -Math.sin(ang));
                TransformUtils.applyFacing(c, new Vector3f(0.05f, 0.05f, 1.8f), inward, PIVOT, 1);
                TransformUtils.applyFacing(g, new Vector3f(0.25f, 0.25f, 1.8f), inward, PIVOT, 1);
            }
        }

        private void spawnCut() {
            Location loc = anchor.clone().add(0, 1.0, 0);
            cutCore = DisplayUtils.spawnBlock(loc, BLADE_CORE, glowOpts(Color.WHITE));
            cutGlow = DisplayUtils.spawnBlock(loc, BLADE_GLOW, glowOpts(RED_GLOW));
            rig.entities().add(cutCore);
            rig.entities().add(cutGlow);
            Vector right = rightVector();
            Vector3f rd = new Vector3f((float) right.getX(), 0f, (float) right.getZ());
            TransformUtils.applyFacing(cutCore, new Vector3f(0.06f, 0.06f, 0.4f), rd, PIVOT, 1);
            TransformUtils.applyFacing(cutGlow, new Vector3f(0.30f, 0.30f, 0.4f), rd, PIVOT, 1);
        }

        private void spawnShards() {
            for (int i = 0; i < 12; i++) {
                Color glow = (i % 2 == 0) ? Color.WHITE : RED_GLOW;
                Location loc = anchor.clone().add(0, 1.0, 0);
                BlockDisplay sh = DisplayUtils.spawnBlock(loc, BLADE_CORE, glowOpts(glow));
                rig.entities().add(sh);
                shards.add(sh);
                double ang = (Math.PI * 2.0 * i) / 12.0;
                Vector3f radial = new Vector3f((float) Math.cos(ang), 0f, (float) Math.sin(ang));
                TransformUtils.applyFacing(sh, new Vector3f(0.06f, 0.06f, 0.6f), radial, PIVOT, 1);
            }
        }

        // ---- animation ----

        private void animateRing() {
            if (ring.isEmpty()) return;
            double contraction;
            if (tick < 18) {
                contraction = 1.0;
            } else {
                contraction = Math.max(0.0, (24 - tick) / 6.0); // 1 at t18 → 0 at t24
            }
            double rot = Math.toRadians(tick * 0.6);
            for (int i = 0; i < ring.size(); i++) {
                BlockDisplay bd = ring.get(i);
                if (bd == null || !bd.isValid()) continue;
                double base = (Math.PI * 2.0 * i) / RING_SEGMENTS;
                double ang = base + rot;
                double r = RING_RADIUS * contraction;
                Location loc = anchor.clone().add(Math.cos(ang) * r, RING_Y, Math.sin(ang) * r);
                bd.teleport(loc);
                Vector3f radial = new Vector3f((float) Math.cos(ang), 0f, (float) Math.sin(ang));
                TransformUtils.applyFacing(bd, new Vector3f(0.05f, 0.03f, 0.5f), radial, PIVOT, 1);
            }
        }

        private void animateSeals() {
            if (seals.isEmpty()) return;
            double p = clamp01((tick - 2) / 10.0); // converge t2 → t12
            double chestY = anchor.getY() + 1.0;
            double roll = Math.toRadians(tick * 4.0);
            for (int i = 0; i < seals.size(); i++) {
                TextDisplay td = seals.get(i);
                if (td == null || !td.isValid()) continue;
                double[] dir = CARDINALS[i];
                double sx = anchor.getX() + dir[0] * 2.2;
                double sz = anchor.getZ() + dir[1] * 2.2;
                double x = sx + (anchor.getX() - sx) * p;
                double z = sz + (anchor.getZ() - sz) * p;
                Location to = new Location(world, x, anchor.getY() + 1.2 + (chestY - (anchor.getY() + 1.2)) * p, z);
                TechniqueDisplayOps.moveDisplay(td, to, 0.6f, (float) roll);
                if (tick >= 14) {
                    double fp = clamp01((tick - 14) / 6.0); // fade t14 → t20
                    td.setTextOpacity((byte) Math.max(0, Math.round(255 * (1.0 - fp))));
                }
            }
        }

        private void animateBlades() {
            if (bladeCores.isEmpty()) return;
            double p = clamp01((tick - 4) / 12.0); // orbit radius 2.4 → 0 over t4..t16
            double radius = 2.4 * (1.0 - p);
            for (int i = 0; i < bladeCores.size(); i++) {
                BlockDisplay c = bladeCores.get(i);
                BlockDisplay g = bladeGlows.get(i);
                if (c == null || !c.isValid() || g == null || !g.isValid()) continue;
                double ang = (Math.PI * 2.0 * i) / 3.0 + tick * 0.5;
                double x = Math.cos(ang) * radius;
                double z = Math.sin(ang) * radius;
                Location loc = anchor.clone().add(x, 1.0, z);
                c.teleport(loc);
                g.teleport(loc);
                Vector3f inward = new Vector3f((float) -Math.cos(ang), 0f, (float) -Math.sin(ang));
                TransformUtils.applyFacing(c, new Vector3f(0.05f, 0.05f, 1.8f), inward, PIVOT, 1);
                TransformUtils.applyFacing(g, new Vector3f(0.25f, 0.25f, 1.8f), inward, PIVOT, 1);
            }
        }

        private void animateCut() {
            if (cutCore == null || cutGlow == null) return;
            if (!cutCore.isValid() || !cutGlow.isValid()) return;
            float coreThick;
            float glowThick;
            float length;
            if (tick <= 14) {
                double p = clamp01((tick - 12) / 2.0); // grow 0.4 → 3.2 over 2 ticks
                length = (float) (0.4 + (3.2 - 0.4) * p);
                coreThick = 0.06f;
                glowThick = 0.30f;
            } else {
                length = 3.2f;
                double p = clamp01((tick - 14) / 2.0); // thin to 0.02 by t16
                coreThick = (float) (0.06 + (0.02 - 0.06) * p);
                glowThick = (float) (0.30 + (0.02 - 0.30) * p);
            }
            Location loc = anchor.clone().add(0, 1.0, 0);
            cutCore.teleport(loc);
            cutGlow.teleport(loc);
            Vector right = rightVector();
            Vector3f rd = new Vector3f((float) right.getX(), 0f, (float) right.getZ());
            TransformUtils.applyFacing(cutCore,
                    new Vector3f(coreThick, coreThick, length), rd, PIVOT, 1);
            TransformUtils.applyFacing(cutGlow,
                    new Vector3f(glowThick, glowThick, length), rd, PIVOT, 1);
        }

        private void animateVerdict() {
            // TitleAnimator reveals ~0.12s per glyph; compress the reveal to fit the 10-tick
            // verdict window (t14..23) instead of showing only "FINA".
            double elapsed = (tick - 14) * 0.22;
            boolean last = tick >= 22;
            var title = TitleAnimator.build("FINAL VERDICT", "終末の裁き", "Final Verdict", elapsed, last);
            caster.showTitle(title);
            for (Player pl : world.getPlayers()) {
                if (pl.equals(caster)) continue;
                if (pl.getLocation().distanceSquared(anchor) <= 144.0) {
                    pl.showTitle(title);
                }
            }
        }

        private void animateShards() {
            if (shards.isEmpty()) return;
            if (tick >= 16 && tick <= 21) {
                double p = clamp01((tick - 16) / 5.0); // radius 0 → 3 over 5 ticks
                double radius = 3.0 * p;
                for (int i = 0; i < shards.size(); i++) {
                    BlockDisplay sh = shards.get(i);
                    if (sh == null || !sh.isValid()) continue;
                    double ang = (Math.PI * 2.0 * i) / 12.0;
                    Location loc = anchor.clone().add(Math.cos(ang) * radius, 1.0, Math.sin(ang) * radius);
                    sh.teleport(loc);
                    Vector3f radial = new Vector3f((float) Math.cos(ang), 0f, (float) Math.sin(ang));
                    TransformUtils.applyFacing(sh, new Vector3f(0.06f, 0.06f, 0.6f), radial, PIVOT, 1);
                }
            }
            if (tick == 21 && !severed) {
                severed = true;
                for (BlockDisplay sh : shards) BlockDisplayOps.shrink(sh, 4);
            }
        }

        // ---- gameplay / cleanup ----

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

        private void lightBurst(double radius) {
            TechniqueLighting.setLightBurst(rig, world, anchor.clone().add(0, 1, 0), radius, 15);
        }

        private void cleanup() {
            for (Entity e : rig.entities()) {
                if (e != null && e.isValid()) e.remove();
            }
            rig.entities().clear();
            TechniqueLighting.setLights(rig.lights(), world, Map.of());
            rig.lights().clear();

            CameraShake.restore(caster);

            // restore target
            if (target.isOnline()) {
                target.setVelocity(savedVelocity);
                target.setNoDamageTicks(savedNoDamageTicks);
            }

            ring.clear();
            seals.clear();
            bladeCores.clear();
            bladeGlows.clear();
            shards.clear();
            cutCore = null;
            cutGlow = null;
        }

        // ---- small helpers ----

        private static DisplayUtils.Options glowOpts(Color glow) {
            return DisplayUtils.Options.builder()
                    .viewRange(8.0f)
                    .brightness(15)
                    .interpolation(0, 1)
                    .teleportDuration(1)
                    .glowColor(glow)
                    .glowing(true)
                    .build();
        }

        private static void discard(Entity e) {
            if (e == null) return;
            if (e.isValid()) e.remove();
        }

        private static double clamp01(double v) {
            if (v < 0) return 0;
            if (v > 1) return 1;
            return v;
        }

        /** Normalized horizontal caster→target direction. */
        private Vector castToTarget() {
            Vector f = target.getLocation().toVector().subtract(caster.getLocation().toVector());
            f.setY(0);
            if (f.lengthSquared() < 1.0e-6) f = new Vector(0, 0, 1);
            f.normalize();
            return f;
        }

        /** Horizontal right vector perpendicular to the caster→target direction. */
        private Vector rightVector() {
            Vector f = castToTarget();
            return new Vector(-f.getZ(), 0, f.getX());
        }
    }
}