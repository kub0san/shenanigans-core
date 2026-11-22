package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import com.hizeric.shenaniganscore.core.display.DisplayUtils;
import com.hizeric.shenaniganscore.core.display.QuadUtils;
import com.hizeric.shenaniganscore.core.display.TransformUtils;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * The compressed mass of cursed spirits: a black core wrapped in two turning shells of violet glass,
 * with a soft red-violet halo. It starts as nothing and swells through the charge, turning faster
 * and brighter, until the blast.
 *
 * <p>Built entirely from display entities: three rotated black cubes for the core, two glass cubes
 * for the shells (re-rotated every few ticks with interpolation, so they turn smoothly), and a few
 * billboard {@link QuadUtils} quads for the halo.</p>
 */
public final class CoreOrb {

    private static final float SEED = 0.01f;
    private static final Color GLOW = Color.fromRGB(190, 120, 255);

    private final Location at;
    private final float size;
    private final List<BlockDisplay> core = new ArrayList<>();
    private final List<BlockDisplay> shells = new ArrayList<>();
    private final List<TextDisplay> halo = new ArrayList<>();
    private final float[][] coreOrient = {{0f, 0f, 0f}, {45f, 35f, 0f}, {20f, 0f, 55f}};

    private int tick;
    private int growStart = -1;
    private int growTicks = 40;
    private float spin;
    private boolean released;
    private boolean closed;

    public CoreOrb(Location at, double size, List<Entity> sink) {
        this.at = at.clone();
        this.size = (float) Math.max(1.0, size);

        for (int i = 0; i < 3; i++) {
            BlockDisplay d = DisplayUtils.spawnBlock(at, Material.BLACK_CONCRETE, 6.0f, 2);
            TransformUtils.applyTransform(d, SEED, SEED, SEED, 0f, 0f, 0f, 0);
            core.add(d);
            if (sink != null) sink.add(d);
        }
        Material[] glass = {Material.PURPLE_STAINED_GLASS, Material.MAGENTA_STAINED_GLASS};
        for (int i = 0; i < 2; i++) {
            BlockDisplay d = DisplayUtils.spawnBlock(at, glass[i], 6.0f, 15);
            d.setGlowing(true);
            d.setGlowColorOverride(GLOW);
            TransformUtils.applyTransform(d, SEED, SEED, SEED, 0f, 0f, 0f, 0);
            shells.add(d);
            if (sink != null) sink.add(d);
        }
        for (int i = 0; i < 3; i++) {
            TextDisplay q = QuadUtils.spawn(at, Display.Billboard.CENTER, 6.0f, false);
            halo.add(q);
            if (sink != null) sink.add(q);
        }
    }

    public Location location() {
        return at;
    }

    /** Starts the swell: the orb grows to full size over {@code ticks}. */
    public void grow(int ticks) {
        if (growStart >= 0 || closed) return;
        growStart = tick;
        growTicks = Math.max(10, ticks);
    }

    /** 0 before it starts, 1 when fully grown. */
    public double progress() {
        if (growStart < 0) return 0.0;
        return Math.min(1.0, (tick - growStart) / (double) growTicks);
    }

    public void tick() {
        if (closed || released || growStart < 0) return;
        tick++;
        double p = progress();
        double eased = p * p;

        // Cores and shells swell; the closer to the blast, the faster the shells turn.
        if ((tick - growStart) % 4 == 0) {
            float s = (float) (size * (0.15 + 0.85 * eased));
            for (int i = 0; i < core.size(); i++) {
                float[] o = coreOrient[i];
                if (core.get(i).isValid()) {
                    TransformUtils.applyTransform(core.get(i), s * 0.7f, s * 0.7f, s * 0.7f, o[0] + spin, o[1], o[2], 4);
                }
            }
            spin += (float) (8.0 + 40.0 * eased);
            for (int i = 0; i < shells.size(); i++) {
                BlockDisplay d = shells.get(i);
                if (!d.isValid()) continue;
                float shell = s * (i == 0 ? 1.15f : 1.55f);
                float dir = i == 0 ? 1f : -1f;
                TransformUtils.applyTransform(d, shell, shell, shell, dir * spin * 0.8f, 20f * dir, 15f, 4);
            }
            float[] sizes = {s * 2.4f, s * 3.6f, s * 5.2f};
            int[] alphas = {(int) (24 + 30 * eased), (int) (14 + 22 * eased), (int) (8 + 14 * eased)};
            Color[] colors = {Color.fromRGB(200, 90, 255), Color.fromRGB(170, 50, 120), Color.fromRGB(120, 40, 200)};
            for (int i = 0; i < halo.size(); i++) {
                QuadUtils.setColor(halo.get(i), colors[i], alphas[i]);
                QuadUtils.apply(halo.get(i), sizes[i], sizes[i], 0f, 0f, 0f, spin * (i + 1) * 0.3f, 4);
            }
        }
    }

    /** The blast: the shells burst open and everything vanishes. */
    public void release() {
        if (released || closed) return;
        released = true;
        for (BlockDisplay d : shells) {
            if (d.isValid()) TransformUtils.applyTransform(d, size * 4.5f, size * 4.5f, size * 4.5f, spin, 20f, 15f, 5);
        }
        for (BlockDisplay d : core) {
            if (d.isValid()) TransformUtils.applyTransform(d, SEED, SEED, SEED, 0f, 0f, 0f, 3);
        }
        for (TextDisplay q : halo) {
            QuadUtils.apply(q, size * 10f, size * 10f, 0f, 0f, 0f, 0f, 5);
            QuadUtils.setColor(q, Color.fromRGB(255, 255, 255), 90);
        }
    }

    /** Fades everything away where it hangs. */
    public void fade(int ticks) {
        int t = Math.max(2, Math.min(59, ticks));
        for (BlockDisplay d : shells) {
            if (d.isValid()) TransformUtils.applyTransform(d, SEED, SEED, SEED, 0f, 0f, 0f, t);
        }
        for (TextDisplay q : halo) QuadUtils.apply(q, 0.01f, 0.01f, 0f, 0f, 0f, 0f, t);
    }

    public void close() {
        if (closed) return;
        closed = true;
        for (BlockDisplay d : core) if (d.isValid()) d.remove();
        for (BlockDisplay d : shells) if (d.isValid()) d.remove();
        for (TextDisplay q : halo) if (q.isValid()) q.remove();
        core.clear();
        shells.clear();
        halo.clear();
    }
}
