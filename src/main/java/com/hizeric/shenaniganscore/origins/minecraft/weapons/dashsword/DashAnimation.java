package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Pure geometry and timing for the Rift Cleave dash-sword sequence.
 *
 * <p>No Bukkit scheduler, player or world state lives here — only frame counts, ticks and vectors.
 * {@link DashSwordEffect} is the thin driver that turns these frames into displays and sounds.</p>
 */
public final class DashAnimation {

    public static final int TRAIL_TICKS = 28;
    public static final int GHOST_COUNT = 1;
    public static final int GHOST_LIFE = 12;
    public static final double GHOST_ARC_RADIUS = 1.4;

    private static final Vector UP = new Vector(0, 1, 0);

    public enum Kind {
        CLEAVE_BURST, RIFT_RIBBON, GHOST_ARC, WIND_CHARGE
    }

    public record Beat(Kind kind, int from, int to, int every) {
        public boolean contains(int tick) {
            return tick >= from && tick <= to;
        }

        public boolean fires(int tick) {
            return contains(tick) && (tick - from) % every == 0;
        }
    }

    /** A display burst requested on a frame. Materials are chosen by the driver. */
    public record DisplaySpawn(Kind kind, int count, float scale) { }

    public enum SoundCue {
        SWING, RIFT_BREATH, RIFT_CLOSE
    }

    public record Sound(int tick, SoundCue cue, float volume, float pitch) { }

    public record Frame(int tick, List<DisplaySpawn> spawns, List<Sound> sounds,
                        boolean swing, boolean shake) { }

    private static final Map<Kind, Beat> BEATS = Map.of(
            Kind.CLEAVE_BURST, new Beat(Kind.CLEAVE_BURST, 0, 2, 1),
            Kind.RIFT_RIBBON, new Beat(Kind.RIFT_RIBBON, 1, 14, 1),
            Kind.GHOST_ARC, new Beat(Kind.GHOST_ARC, 1, 18, 1),
            Kind.WIND_CHARGE, new Beat(Kind.WIND_CHARGE, 0, 6, 3));

    private static final List<Sound> SOUNDS = List.of(
            new Sound(0, SoundCue.SWING, 0.9f, 1.15f),
            new Sound(10, SoundCue.RIFT_BREATH, 0.5f, 1.45f),
            new Sound(20, SoundCue.RIFT_CLOSE, 0.75f, 1.2f));

    private DashAnimation() { }

    public static Beat beat(Kind kind) {
        return BEATS.get(kind);
    }

    /** Right-hand vector for a forward direction, with a fallback for straight-up looks. */
    public static Vector rightOf(Vector forward) {
        Vector candidate = forward.clone().crossProduct(UP);
        if (candidate.lengthSquared() < 1.0e-6) return new Vector(1, 0, 0);
        return candidate.normalize();
    }

    /** Ghost blades shrink over their lifetime and never disappear abruptly. */
    public static float ghostScale(int age) {
        return (float) Math.max(0.05, 1.0 - age / (double) GHOST_LIFE);
    }

    /** Launch timeline: cleave, rift ribbon, wind-charge rings, sounds and shake (ticks 0–20). */
    public static List<Frame> launch() {
        Beat cleave = beat(Kind.CLEAVE_BURST);
        Beat rift = beat(Kind.RIFT_RIBBON);
        Beat wind = beat(Kind.WIND_CHARGE);
        List<Frame> frames = new ArrayList<>();
        for (int tick = 0; tick <= 20; tick++) {
            List<DisplaySpawn> spawns = new ArrayList<>();
            if (cleave.fires(tick)) spawns.add(new DisplaySpawn(Kind.CLEAVE_BURST, 12, 0.55f));
            if (rift.fires(tick)) spawns.add(new DisplaySpawn(Kind.RIFT_RIBBON, 8, 0.35f));
            if (wind.fires(tick)) spawns.add(new DisplaySpawn(Kind.WIND_CHARGE, 12, 0.55f));
            final int currentTick = tick;
            List<Sound> sounds = SOUNDS.stream().filter(sound -> sound.tick() == currentTick).toList();
            frames.add(new Frame(tick, List.copyOf(spawns), sounds,
                    tick == 0, tick == 3 || tick == 6));
        }
        return List.copyOf(frames);
    }

    /** Ghost arc timeline (ticks 0–{@link #TRAIL_TICKS}). */
    public static List<Frame> trail() {
        Beat ghosts = beat(Kind.GHOST_ARC);
        List<Frame> frames = new ArrayList<>();
        for (int tick = 0; tick <= TRAIL_TICKS; tick++) {
            List<DisplaySpawn> spawns = ghosts.fires(tick)
                    ? List.of(new DisplaySpawn(Kind.GHOST_ARC, GHOST_COUNT, 1.0f))
                    : List.of();
            frames.add(new Frame(tick, spawns, List.of(), false, false));
        }
        return List.copyOf(frames);
    }
}
