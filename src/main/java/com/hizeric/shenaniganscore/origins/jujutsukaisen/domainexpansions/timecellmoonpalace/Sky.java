package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.timecellmoonpalace;

import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * The world drains to a cold, moonless midnight as the black sphere closes, then
 * rolls back to the real time on collapse. Per-player, so outsiders keep their sky.
 * (Same approach as the shrine's OpenBarrier, different colour of dread.)
 */
final class Sky {

    private static final long MIDNIGHT = 18000L;
    private static final long DAY = 24000L;

    private Sky() {}

    static void apply(Player p) {
        if (!p.isOnline()) return;
        p.setPlayerTime(MIDNIGHT, false);
        p.setPlayerWeather(WeatherType.CLEAR);
    }

    static void fadeIn(Player p, long fromTime, double progress) {
        if (!p.isOnline()) return;
        p.setPlayerTime(lerp(fromTime, MIDNIGHT, progress), false);
        p.setPlayerWeather(WeatherType.CLEAR);
    }

    static void fadeOut(Player p, double progress) {
        if (!p.isOnline()) return;
        double pr = MathUtil.clamp(progress, 0.0, 1.0);
        if (pr >= 1.0) {
            reset(p);
            return;
        }
        p.setPlayerTime(lerp(MIDNIGHT, p.getWorld().getTime(), pr), false);
        p.setPlayerWeather(WeatherType.CLEAR);
    }

    static void reset(Player p) {
        if (!p.isOnline()) return;
        p.resetPlayerTime();
        p.resetPlayerWeather();
    }

    /** Shortest-path interpolation around the day with smoothstep easing. */
    private static long lerp(long from, long to, double progress) {
        double p = MathUtil.clamp(progress, 0.0, 1.0);
        p = MathUtil.smoothstep(p);
        long delta = (((to - from) % DAY) + DAY) % DAY;
        if (delta > DAY / 2) delta -= DAY;
        long t = from + Math.round(delta * p);
        return ((t % DAY) + DAY) % DAY;
    }
}
