package com.hizeric.shenaniganscore.core.display;

import com.hizeric.shenaniganscore.core.util.ClientBlockUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hizeric.shenaniganscore.core.display.BlockDisplayOps.blockLoc;

/**
 * Shared fake-light plumbing for the JJK technique visuals.
 *
 * <p>Every technique builder used to carry byte-identical copies of {@link #lightable(World, Location)},
 * {@link #setLights(Map, World, Map)} and a 16-level {@code Light[]} cache, plus the four display
 * constants below. The light blocks are client-side only: they are sent through
 * {@link ClientBlockUtils} and tracked per rig so they can be restored when a technique ends.</p>
 */
public final class TechniqueLighting {

    /** Display view range used by every technique builder. */
    public static final float VIEW = 6.0f;
    /** Full-brightness block light. */
    public static final int BRIGHT = 15;
    /** Minimum display scale for a nearly-collapsed piece. */
    public static final float TINY = 0.02f;
    /** Squared range within which a player receives fake light updates. */
    public static final double LIGHT_RANGE_SQ = 128.0 * 128.0;

    private static final Light[] LIGHTS = buildLights();

    private TechniqueLighting() { }

    private static Light[] buildLights() {
        Light[] out = new Light[16];
        for (int i = 0; i < out.length; i++) {
            Light l = (Light) Material.LIGHT.createBlockData();
            l.setLevel(i);
            out[i] = l;
        }
        return out;
    }

    /** True when {@code l} is in a loaded chunk and currently air (so a fake light can be shown). */
    public static boolean lightable(World w, Location l) {
        if (!w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) return false;
        return w.getBlockAt(l.getBlockX(), l.getBlockY(), l.getBlockZ()).getType().isAir();
    }

    /**
     * Reconciles the rig's tracked fake lights with {@code want}, sending only the difference to
     * nearby players. {@code lights} is the owning rig's mutable {@code Map<Location, Integer>};
     * it is cleared and repopulated with {@code want} so the next call can diff again.
     */
    public static void setLights(Map<Location, Integer> lights, World w, Map<Location, Integer> want) {
        Map<Location, BlockData> send = new HashMap<>();
        Location ref = null;

        for (Location old : lights.keySet()) {
            if (want.containsKey(old)) continue;
            if (w.isChunkLoaded(old.getBlockX() >> 4, old.getBlockZ() >> 4)) {
                send.put(old, old.getBlock().getBlockData());
            }
            ref = old;
        }
        for (Map.Entry<Location, Integer> e : want.entrySet()) {
            Integer prev = lights.get(e.getKey());
            if (prev == null || !prev.equals(e.getValue())) {
                send.put(e.getKey(), LIGHTS[Math.max(0, Math.min(15, e.getValue()))]);
            }
            ref = e.getKey();
        }
        lights.clear();
        lights.putAll(want);

        if (send.isEmpty() || ref == null) return;
        for (Player pl : w.getPlayers()) {
            if (pl.getLocation().distanceSquared(ref) <= LIGHT_RANGE_SQ) {
                ClientBlockUtils.sendFakeBlocks(pl, send);
            }
        }
    }

    /** Reconcile two optional point lights, taking the higher level where they overlap. */
    public static void setLightPair(LightingRig rig, World w,
                                    Location a, int levelA, Location b, int levelB) {
        Map<Location, Integer> want = new HashMap<>();
        if (a != null) {
            Location la = blockLoc(a);
            if (lightable(w, la)) want.put(la, levelA);
        }
        if (b != null) {
            Location lb = blockLoc(b);
            if (lightable(w, lb)) want.merge(lb, levelB, Math::max);
        }
        setLights(rig.lights(), w, want);
    }

    /** Reconcile a centre light plus six axis neighbours at 45% of the given radius. */
    public static void setLightBurst(LightingRig rig, World w, Location centre, double R, int level) {
        Map<Location, Integer> want = new HashMap<>();
        double d = R * 0.45;
        double[][] offsets = {{0, 0, 0}, {d, 0, 0}, {-d, 0, 0}, {0, d, 0}, {0, -d, 0}, {0, 0, d}, {0, 0, -d}};
        for (double[] o : offsets) {
            Location l = blockLoc(centre.clone().add(o[0], o[1], o[2]));
            if (lightable(w, l)) want.put(l, level);
        }
        setLights(rig.lights(), w, want);
    }

    /** Reconcile a light at every supplied point, or clear all lights when {@code level <= 0}. */
    public static void setLightPath(LightingRig rig, World w, List<Location> points, int level) {
        Map<Location, Integer> want = new HashMap<>();
        if (level > 0) {
            for (Location p : points) {
                Location l = blockLoc(p);
                if (lightable(w, l)) want.put(l, level);
            }
        }
        setLights(rig.lights(), w, want);
    }
}
