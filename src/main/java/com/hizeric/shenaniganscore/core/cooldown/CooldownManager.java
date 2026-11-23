package com.hizeric.shenaniganscore.core.cooldown;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks per-player cooldown end timestamps (millis).
 *
 * <p>Abilities use the keyed API ({@code UUID -> ability id -> endsAt}) so casting Blue does not
 * lock Red. The original single-key methods remain as deprecated delegates to the {@code global}
 * key for any code that still uses them.</p>
 */
public class CooldownManager {

    private static final String GLOBAL_KEY = "global";

    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Long>> keyedCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> cooldownTotals = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Integer>> keyedCooldownTotals = new ConcurrentHashMap<>();

    /** @deprecated use {@link #setCooldown(UUID, String, int)} with the ability id. */
    @Deprecated
    public void setCooldown(UUID uuid, int seconds) {
        setCooldown(uuid, GLOBAL_KEY, seconds);
    }

    /** @deprecated use {@link #isOnCooldown(UUID, String)} with the ability id. */
    @Deprecated
    public boolean isOnCooldown(UUID uuid) {
        return isOnCooldown(uuid, GLOBAL_KEY);
    }

    /** @deprecated use {@link #getRemainingSeconds(UUID, String)} with the ability id. */
    @Deprecated
    public int getRemainingSeconds(UUID uuid) {
        return getRemainingSeconds(uuid, GLOBAL_KEY);
    }

    /** Puts the given player on cooldown for {@code seconds} under a specific key. */
    public void setCooldown(UUID uuid, String key, int seconds) {
        if (uuid == null || key == null || seconds <= 0) return;
        long endsAt = System.currentTimeMillis() + (seconds * 1000L);
        if (GLOBAL_KEY.equals(key)) {
            cooldowns.put(uuid, endsAt);
            cooldownTotals.put(uuid, seconds);
        } else {
            keyedCooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(uuid, endsAt);
            keyedCooldownTotals.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(uuid, seconds);
        }
    }

    /**
     * Shortens an active cooldown by {@code seconds} without ever extending or reviving it.
     *
     * <p>The recorded total is deliberately left alone: gauges read it as the original duration
     * while the remaining time moves independently. A reduction past the end time clamps at now,
     * so the cooldown simply finishes early.</p>
     *
     * @return {@code true} when an active cooldown was actually shortened
     */
    public boolean reduceCooldown(UUID uuid, String key, int seconds) {
        if (uuid == null || key == null || seconds <= 0) return false;
        long now = System.currentTimeMillis();
        long reduction = seconds * 1000L;
        boolean[] changed = {false};
        if (GLOBAL_KEY.equals(key)) {
            cooldowns.compute(uuid, (id, endsAt) -> shortened(endsAt, now, reduction, changed));
        } else {
            keyedCooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                    .compute(uuid, (id, endsAt) -> shortened(endsAt, now, reduction, changed));
        }
        return changed[0];
    }

    private static Long shortened(Long endsAt, long now, long reduction, boolean[] changed) {
        if (endsAt == null || endsAt <= now) return endsAt;
        changed[0] = true;
        return Math.max(now, endsAt - reduction);
    }

    /**
     * Atomically claims a cooldown: returns {@code true} only if the player was
     * free and the cooldown is now set. Check-then-set in one operation so two
     * concurrent casts cannot both pass an {@link #isOnCooldown(UUID, String)}
     * probe and double-fire (F-22).
     *
     * <p>A non-positive duration means "no cooldown configured": the claim always
     * succeeds and no state is written, matching {@link #setCooldown(UUID, String, int)}.</p>
     */
    public boolean tryClaim(UUID uuid, String key, int seconds) {
        if (uuid == null || key == null) return false;
        if (seconds <= 0) return true;
        long now = System.currentTimeMillis();
        long endsAt = now + (seconds * 1000L);
        Map<UUID, Long> target = GLOBAL_KEY.equals(key)
                ? cooldowns
                : keyedCooldowns.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
        // ConcurrentHashMap.compute is atomic per key; the remapping function
        // runs once while holding the bin lock.
        boolean[] claimed = {false};
        target.compute(uuid, (id, current) -> {
            if (current != null && current > now) return current; // still cooling — no claim
            claimed[0] = true;
            return endsAt;
        });
        if (claimed[0]) {
            if (GLOBAL_KEY.equals(key)) {
                cooldownTotals.put(uuid, seconds);
            } else {
                keyedCooldownTotals.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                        .put(uuid, seconds);
            }
        }
        return claimed[0];
    }

    /** @return whether the given player is still on cooldown for this key. */
    public boolean isOnCooldown(UUID uuid, String key) {
        return getRemainingSeconds(uuid, key) > 0;
    }

    /** @return how many whole seconds remain for this key (0 when free). */
    public int getRemainingSeconds(UUID uuid, String key) {
        if (uuid == null || key == null) return 0;
        Long endsAt = GLOBAL_KEY.equals(key)
                ? cooldowns.get(uuid)
                : keyedCooldowns.getOrDefault(key, Map.of()).get(uuid);
        if (endsAt == null) return 0;
        long remaining = endsAt - System.currentTimeMillis();
        return remaining <= 0 ? 0 : (int) Math.ceil(remaining / 1000.0);
    }

    /** Remaining cooldown in milliseconds, for fine-grained gauge fills. */
    public long getRemainingMillis(UUID uuid, String key) {
        if (uuid == null || key == null) return 0;
        Long endsAt = GLOBAL_KEY.equals(key)
                ? cooldowns.get(uuid)
                : keyedCooldowns.getOrDefault(key, Map.of()).get(uuid);
        if (endsAt == null) return 0;
        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    /** Total seconds originally set for this key, or 0 when there is no cooldown. */
    public int getTotalSeconds(UUID uuid, String key) {
        if (uuid == null || key == null) return 0;
        Integer total = GLOBAL_KEY.equals(key)
                ? cooldownTotals.get(uuid)
                : keyedCooldownTotals.getOrDefault(key, Map.of()).get(uuid);
        return total == null ? 0 : total;
    }

    /** Clears one key for a player. */
    public void clear(UUID uuid, String key) {
        if (uuid == null || key == null) return;
        if (GLOBAL_KEY.equals(key)) {
            cooldowns.remove(uuid);
            cooldownTotals.remove(uuid);
        } else {
            Map<UUID, Long> bucket = keyedCooldowns.get(key);
            if (bucket != null) bucket.remove(uuid);
            Map<UUID, Integer> totals = keyedCooldownTotals.get(key);
            if (totals != null) totals.remove(uuid);
        }
    }

    /** @deprecated use {@link #clear(UUID, String)}. */
    @Deprecated
    public void clear(UUID uuid) {
        clear(uuid, GLOBAL_KEY);
    }

    /** Clears every cooldown belonging to a player (respawn, round reset, quit). */
    public void clearAll(UUID uuid) {
        if (uuid == null) return;
        cooldowns.remove(uuid);
        cooldownTotals.remove(uuid);
        for (Map<UUID, Long> bucket : keyedCooldowns.values()) bucket.remove(uuid);
        for (Map<UUID, Integer> totals : keyedCooldownTotals.values()) totals.remove(uuid);
    }

    /** Sweeps finished entries so the maps stay small across long uptimes. */
    public void clearExpired() {
        long now = System.currentTimeMillis();
        cooldowns.values().removeIf(endsAt -> endsAt <= now);
        keyedCooldowns.values().removeIf(bucket -> {
            bucket.values().removeIf(endsAt -> endsAt <= now);
            return bucket.isEmpty();
        });
    }

    /** Number of distinct cooldown keys currently tracked (diagnostics). */
    public int activeKeyCount() {
        return keyedCooldowns.size();
    }

    /** Guards against accidental use of a null key from a caller. */
    public static String requireKey(String key) {
        return Objects.requireNonNull(key, "cooldown key");
    }
}
