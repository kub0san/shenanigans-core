package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.wombprofusion;

import org.bukkit.WeatherType;
import org.bukkit.entity.Player;

/**
 * The open domain has no shell, so the sky itself sickens: the light sinks to a dim, bruised dusk
 * and a thin rain falls, for every player inside. Per-player, so outsiders see the real sky.
 */
final class Sky {

    /** A dim dusk: dark enough to feel wrong, light enough to read the tower. */
    private static final long GLOOM = 13400L;
    private static final long DAY = 24000L;

    private Sky() {
    }

    static void fadeIn(Player p, long fromTime, double progress) {
        if (!p.isOnline()) return;
        p.setPlayerTime(lerp(fromTime, GLOOM, progress), false);
        p.setPlayerWeather(WeatherType.DOWNFALL);
    }

    static void hold(Player p) {
        if (!p.isOnline()) return;
        p.setPlayerTime(GLOOM, false);
        p.setPlayerWeather(WeatherType.DOWNFALL);
    }

    static void fadeOut(Player p, double progress) {
        if (!p.isOnline()) return;
        double pr = Math.max(0.0, Math.min(1.0, progress));
        if (pr >= 1.0) {
            reset(p);
            return;
        }
        p.setPlayerTime(lerp(GLOOM, p.getWorld().getTime(), pr), false);
    }

    static void reset(Player p) {
        if (!p.isOnline()) return;
        p.resetPlayerTime();
        p.resetPlayerWeather();
    }

    /** Shortest-path interpolation around the day with smoothstep easing. */
    private static long lerp(long from, long to, double progress) {
        double p = Math.max(0.0, Math.min(1.0, progress));
        p = p * p * (3.0 - 2.0 * p);
        long delta = (((to - from) % DAY) + DAY) % DAY;
        if (delta > DAY / 2) delta -= DAY;
        long t = from + Math.round(delta * p);
        return ((t % DAY) + DAY) % DAY;
    }
}
