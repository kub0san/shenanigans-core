package com.hizeric.shenaniganscore.battleground.deploy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure animation geometry for "Starfall Lock". Each phase returns a {@link Frame} of particle beats,
 * sound cues and display cues; {@link SkyDrop} maps them onto Bukkit. Kept pure so tuning the visuals
 * is a matter of editing lists rather than reading a tick loop.
 */
public final class DropAnimation {

    public enum Kind {
        DUST, DUST_TRANSITION, END_ROD, SOUL_FLAME, ELECTRIC_SPARK,
        SONIC_BOOM, EXPLOSION, FLASH, CLOUD, ENCHANT, WAX, TOTEM
    }

    /** One particle emission. RGB fields are only used by the dust variants. */
    public record Beat(Kind kind, double x, double y, double z, int count,
                       double dx, double dy, double dz, double speed,
                       int rgbA, int rgbB, float size) {
        public static Beat at(Kind kind, double x, double y, double z) {
            return new Beat(kind, x, y, z, 1, 0, 0, 0, 0, 0x5AC8FF, 0xFFFFFF, 1.0f);
        }
    }

    public record Sound(String key, float volume, float pitch) { }

    public enum DisplayKind { LOCK_TEXT, IMPACT_TEXT, AFTERIMAGE }

    public record Display(DisplayKind kind, double x, double y, double z,
                          String text, int lifeTicks, float scale) { }

    public record Frame(List<Beat> beats, List<Sound> sounds, List<Display> displays) { }

    public static final int LOCK_TICKS = 15;
    public static final int IMPACT_TICKS = 20;

    private DropAnimation() { }

    /** Phase 1 — a ground ring expands cyan→white while an end-rod column blinks up the target. */
    public static Frame lockOn(double x, double y, double z, int tick) {
        List<Beat> beats = new ArrayList<>();
        List<Sound> sounds = new ArrayList<>();
        List<Display> displays = new ArrayList<>();
        double radius = 0.4 + tick * 0.22;
        int points = 32;
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            beats.add(new Beat(Kind.DUST_TRANSITION,
                    x + Math.cos(a) * radius, y + 0.15, z + Math.sin(a) * radius,
                    1, 0, 0, 0, 0, 0x5AC8FF, 0xFFFFFF, 0.9f));
        }
        if (tick % 2 == 0) {
            beats.add(new Beat(Kind.END_ROD, x, y + 1 + tick * 0.3, z,
                    1, 0, 0.05, 0, 0, 0x5AC8FF, 0xFFFFFF, 1.0f));
        }
        if (tick == 0) {
            sounds.add(new Sound("BLOCK_BEACON_ACTIVATE", 0.8f, 1.4f));
            sounds.add(new Sound("ENTITY_WARDEN_SONIC_CHARGE", 0.4f, 1.9f));
        }
        if (tick == LOCK_TICKS - 2) {
            displays.add(new Display(DisplayKind.LOCK_TEXT, x, y + 2.4, z,
                    "\u00a7b\u25bc TARGET LOCKED", 30, 1.0f));
        }
        return new Frame(beats, sounds, displays);
    }

    /** Phase 2 — three interleaved helixes, a warp ring every 4 ticks, fading afterimages. */
    public static Frame descent(double x, double y, double z, int tick) {
        List<Beat> beats = new ArrayList<>();
        List<Sound> sounds = new ArrayList<>();
        List<Display> displays = new ArrayList<>();
        Kind[] kinds = {Kind.END_ROD, Kind.SOUL_FLAME, Kind.ELECTRIC_SPARK};
        double[] radii = {0.45, 0.8, 1.15};
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(tick * 55 + i * 120);
            beats.add(Beat.at(kinds[i],
                    x + Math.cos(a) * radii[i], y + 1.0, z + Math.sin(a) * radii[i]));
        }
        if (tick % 4 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                beats.add(new Beat(Kind.DUST_TRANSITION,
                        x + Math.cos(a) * 1.6, y + 0.4, z + Math.sin(a) * 1.6,
                        1, 0, 0, 0, 0, 0x5AC8FF, 0xFFFFFF, 0.7f));
            }
        }
        if (tick == 0) sounds.add(new Sound("ENTITY_ENDER_DRAGON_FLAP", 0.5f, 0.7f));
        if (tick % 6 == 0) sounds.add(new Sound("BLOCK_BUBBLE_COLUMN_WHIRLPOOL_INSIDE", 0.3f, 1.6f));
        if (tick % 5 == 0) {
            displays.add(new Display(DisplayKind.AFTERIMAGE, x, y + 1.0, z,
                    "\u00a7b\u2726", 8, 0.8f));
        }
        return new Frame(beats, sounds, displays);
    }

    /** Phase 3 — one-shot smash cues, three expanding rings, a jittering end-rod pillar. */
    public static Frame impact(double x, double y, double z, int tick) {
        List<Beat> beats = new ArrayList<>();
        List<Sound> sounds = new ArrayList<>();
        List<Display> displays = new ArrayList<>();
        if (tick == 0) {
            beats.add(new Beat(Kind.EXPLOSION, x, y + 0.4, z, 1, 0, 0, 0, 0, 0, 0, 0));
            beats.add(new Beat(Kind.SONIC_BOOM, x, y + 0.4, z, 1, 0, 0, 0, 0, 0, 0, 0));
            beats.add(new Beat(Kind.FLASH, x, y + 0.5, z, 2, 0, 0, 0, 0, 0, 0, 0));
            beats.add(new Beat(Kind.CLOUD, x, y + 0.5, z, 28, 1.4, 0.25, 1.4, 0.05, 0, 0, 0));
            sounds.add(new Sound("ITEM_MACE_SMASH_GROUND_HEAVY", 1.2f, 0.85f));
            sounds.add(new Sound("BLOCK_AMETHYST_BLOCK_RESONATE", 0.9f, 1.6f));
            sounds.add(new Sound("ENTITY_GENERIC_EXPLODE", 0.7f, 1.3f));
        }
        for (int ring = 0; ring < 3; ring++) {
            double r = (tick + ring * 4) * 0.35;
            if (r <= 0 || r > 4.5) continue;
            int points = 28;
            for (int i = 0; i < points; i++) {
                double a = Math.PI * 2 * i / points;
                beats.add(new Beat(Kind.DUST_TRANSITION,
                        x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r,
                        1, 0, 0, 0, 0, 0x5AC8FF, 0xFFFFFF, 0.9f));
            }
        }
        if (tick % 2 == 0) {
            for (double h = 0; h < 5.0; h += 0.4) {
                double jx = ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
                double jz = ThreadLocalRandom.current().nextDouble(-0.3, 0.3);
                beats.add(new Beat(Kind.END_ROD, x + jx, y + h + tick * 0.05, z + jz,
                        1, 0.05, 0.15, 0.05, 0.02, 0x5AC8FF, 0xFFFFFF, 1.0f));
            }
        }
        // Phase 4 — afterglow: once the shock rings fade, dust motes keep rising for the rest of
        // the impact window so the landing never drops to a dead frame.
        if (tick >= 10) {
            for (int i = 0; i < 6; i++) {
                double a = (Math.PI * 2 * i) / 6 + tick * 0.11;
                double radius = 0.8 + 0.06 * (tick - 10);
                beats.add(new Beat(Kind.DUST_TRANSITION,
                        x + Math.cos(a) * radius, y + 0.3 + (tick - 10) * 0.12,
                        z + Math.sin(a) * radius,
                        1, 0, 0.06, 0, 0, 0x5AC8FF, 0xFFFFFF, 0.6f));
            }
        }
        if (tick == 6) {
            displays.add(new Display(DisplayKind.IMPACT_TEXT, x, y + 2.6, z,
                    "\u00a7b\u2739 IMPACT \u2739", 20, 1.2f));
        }
        return new Frame(beats, sounds, displays);
    }
}
