package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Player-owned hit charges. State lives in the player's PDC; regeneration is lazy and timestamp
 * based, so it works across reconnects and while offline. No scheduled task, no CooldownManager.
 */
public final class MomentumMaceChargeManager {
    private static final NamespacedKey CHARGES = new NamespacedKey("shenaniganscore", "momentum_mace_charges");
    private static final NamespacedKey NEXT = new NamespacedKey("shenaniganscore", "momentum_mace_next_recharge");

    private MomentumMaceChargeManager() { }

    /** Config-backed maximum charges (1..10). */
    public static int maxCharges() {
        return AbilityTuningConfig.current().boundedInt(AbilityTuningConfig.Section.ITEMS,
                "momentum-mace", "max-hit-charges", 3, 1, 10);
    }

    /** Config-backed recharge interval in milliseconds (1..300 seconds). */
    public static long rechargeMs() {
        return AbilityTuningConfig.current().boundedInt(AbilityTuningConfig.Section.ITEMS,
                "momentum-mace", "hit-charge-recharge", 10, 1, 300) * 1000L;
    }

    /** Pure, overflow-safe recharge calculation — testable without Bukkit. */
    public record Refresh(int charges, long nextRechargeMillis) { }

    public static Refresh calculate(int storedCharges, long storedNext, long now, long interval, int max) {
        int charges = Math.max(0, Math.min(max, storedCharges));
        if (charges >= max) return new Refresh(charges, 0L);
        long next = storedNext > 0 ? storedNext : now + interval;
        if (now < next) return new Refresh(charges, next);
        long elapsed = now - next;
        long gainedLong = elapsed / interval + 1L;
        int gained = (int) Math.min((long) max - charges, gainedLong);
        charges += gained;
        if (charges >= max) return new Refresh(charges, 0L);
        return new Refresh(charges, now - (elapsed % interval) + interval);
    }

    public static int getCharges(Player p) { return refresh(p).charges(); }

    public static boolean hasCharge(Player p) { return getCharges(p) > 0; }

    public static Refresh refresh(Player p) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        Refresh result = calculate(
                c.getOrDefault(CHARGES, PersistentDataType.INTEGER, maxCharges()),
                c.getOrDefault(NEXT, PersistentDataType.LONG, 0L),
                System.currentTimeMillis(), rechargeMs(), maxCharges());
        c.set(CHARGES, PersistentDataType.INTEGER, result.charges());
        if (result.nextRechargeMillis() == 0L) c.remove(NEXT);
        else c.set(NEXT, PersistentDataType.LONG, result.nextRechargeMillis());
        return result;
    }

    public static long getNextRechargeMillis(Player p) { return refresh(p).nextRechargeMillis(); }

    public static boolean consumeCharge(Player p) {
        Refresh state = refresh(p);
        if (state.charges() <= 0) return false;
        PersistentDataContainer c = p.getPersistentDataContainer();
        int remaining = state.charges() - 1;
        c.set(CHARGES, PersistentDataType.INTEGER, remaining);
        if (remaining < maxCharges() && state.nextRechargeMillis() == 0L) {
            c.set(NEXT, PersistentDataType.LONG, System.currentTimeMillis() + rechargeMs());
        }
        return true;
    }
}
