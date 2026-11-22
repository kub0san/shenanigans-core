package com.hizeric.shenaniganscore.origins.jujutsukaisen.domainexpansions.malevolentshrine;

import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import com.hizeric.shenaniganscore.core.util.MathUtil;

/**
 * Sukuna's open barrier: range sacrificed for protection.
 *
 * <p>With no physical shell, the domain paints the world in a blood-red
 * sunset for all viewers within reach. The sky <b>time-lapses</b> to dusk
 * as the shrine erupts, then bleeds back out on collapse for a heavier,
 * more cinematic feel.</p>
 *
 * <p>The sky used to come with a blanket blindness, which turned the whole
 * domain into a black screen — the arena was unreadable and the fight was
 * guesswork. The shrine is now <b>stained red, not blinded</b>: the light
 * stays, the dread stays, and everybody can still see what is happening to
 * them.</p>
 */
public final class OpenBarrier {

    /** Deep crimson dusk. */
    private static final long BLOOD_RED_TIME = 12500L;
    private static final long DEEP_CRIMSON_TIME = 12550L;
    private static final long DAY = 24000L;

    private OpenBarrier() {}

    /** Instant stain for players entering mid-domain. */
    public static void applySky(Player player) {
        if (!player.isOnline()) return;
        player.setPlayerTime(DEEP_CRIMSON_TIME, false);
        player.setPlayerWeather(WeatherType.CLEAR);
    }

    /** Awakening: smooth time-lapse into blood-crimson dusk with heavier falloff. */
    public static void fadeIn(Player player, long fromTime, double progress) {
        if (!player.isOnline()) return;
        double p = MathUtil.clamp(progress, 0.0, 1.0);
        // Ease in then accelerate toward full crimson for more drama.
        double eased = p < 0.7 ? (p * p / 0.49) * 0.7 : 0.7 + (p - 0.7) / 0.3 * (1.0 - Math.pow(1.0 - ((p - 0.7) / 0.3), 2.0));
        eased = MathUtil.clamp(eased, 0.0, 1.0);
        long target = p > 0.6 ? lerpTime(BLOOD_RED_TIME, DEEP_CRIMSON_TIME, (p - 0.6) / 0.4) : BLOOD_RED_TIME;
        player.setPlayerTime(lerpTime(fromTime, target, eased), false);
        player.setPlayerWeather(WeatherType.CLEAR);
    }

    /** Collapse: bleed back from crimson to world time with cinematic easing. */
    public static void fadeOut(Player player, double progress) {
        if (!player.isOnline()) return;
        long worldTime = player.getWorld().getTime();
        double p = MathUtil.clamp(progress, 0.0, 1.0);
        if (p >= 1.0) {
            resetSky(player);
            return;
        }
        double eased = 1.0 - Math.pow(1.0 - p, 1.8);
        // Start from deep crimson, ease toward world time.
        long from = p < 0.25 ? lerpTime(DEEP_CRIMSON_TIME, BLOOD_RED_TIME, p / 0.25) : BLOOD_RED_TIME;
        player.setPlayerTime(lerpTime(from, worldTime, eased), false);
        player.setPlayerWeather(WeatherType.CLEAR);
    }

    /** Return sky/weather to world state. */
    public static void resetSky(Player player) {
        if (!player.isOnline()) return;
        player.resetPlayerTime();
        player.resetPlayerWeather();
    }

    /** Shortest-path interpolation around the day with smoothstep easing. */
    private static long lerpTime(long from, long to, double progress) {
        double p = MathUtil.clamp(progress, 0.0, 1.0);
        p = MathUtil.smoothstep(p);
        long delta = (((to - from) % DAY) + DAY) % DAY;
        if (delta > DAY / 2) delta -= DAY;
        long t = from + Math.round(delta * p);
        return ((t % DAY) + DAY) % DAY;
    }
}
