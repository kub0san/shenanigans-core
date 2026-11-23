package com.hizeric.shenaniganscore.core.display;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Small, self-contained camera-shake helper.
 *
 * <p>Records a player's current yaw/pitch as a fixed base, then for {@code durationTicks}
 * applies a random yaw/pitch offset around that base whose amplitude decays linearly to zero.
 * On completion (or on {@link #restore(Player)}) the base rotation is put back exactly once.
 * This is intentionally independent of any domain or technique; the Shrine's own
 * {@code Shake} in {@code malevolentshrine/Session} is untouched.</p>
 *
 * <p>All public methods must be called on the Bukkit main thread.</p>
 */
public final class CameraShake {

    private static final Map<UUID, State> ACTIVE = new ConcurrentHashMap<>();

    private CameraShake() {
    }

    /**
     * Begin a camera shake on {@code player} for {@code durationTicks} ticks.
     *
     * <p>If a shake is already active for this player, its base rotation is restored first
     * (no stacking). Guards: null/offline player, {@code durationTicks <= 0} and
     * non-positive {@code amplitudeDegrees} are ignored.</p>
     */
    public static void start(Plugin plugin, Player player, int durationTicks, float amplitudeDegrees) {
        if (plugin == null || player == null) return;
        if (!player.isOnline() || player.isDead()) return;
        if (durationTicks <= 0 || amplitudeDegrees <= 0f) return;

        UUID id = player.getUniqueId();

        // Replace any existing shake: restore the old base first, cancel its task.
        State previous = ACTIVE.remove(id);
        if (previous != null) previous.cancelAndRestore();

        State state = new State(id, player.getLocation().getYaw(), player.getLocation().getPitch(),
                durationTicks, amplitudeDegrees);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                state.tick();
            }
        }.runTaskTimer(plugin, 0L, 1L);
        state.task = task;
        ACTIVE.put(id, state);
    }

    /**
     * Restore the recorded base rotation for {@code player} and cancel its shake, if any.
     */
    public static void restore(Player player) {
        if (player == null) return;
        State state = ACTIVE.remove(player.getUniqueId());
        if (state != null) state.cancelAndRestore();
    }

    /**
     * Restore and clear every active shake. Safe to call on plugin disable.
     */
    public static void restoreAll() {
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            State state = it.next().getValue();
            it.remove();
            state.cancelAndRestore();
        }
    }

    /** One active shake for a player. Oscillates around a fixed base rotation and decays. */
    private static final class State {
        private final UUID playerId;
        private final float baseYaw;
        private final float basePitch;
        private final int durationTicks;
        private final float amplitude;
        private final ThreadLocalRandom random = ThreadLocalRandom.current();
        private int elapsed;
        private BukkitTask task;

        private State(UUID playerId, float baseYaw, float basePitch, int durationTicks, float amplitude) {
            this.playerId = playerId;
            this.baseYaw = baseYaw;
            this.basePitch = basePitch;
            this.durationTicks = durationTicks;
            this.amplitude = amplitude;
        }

        private void tick() {
            if (task != null && task.isCancelled()) {
                ACTIVE.remove(playerId, this);
                return;
            }

            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead()) {
                cancelAndRestore();
                return;
            }

            elapsed++;
            if (elapsed >= durationTicks) {
                cancelAndRestore();
                return;
            }

            // Linear decay from full amplitude to zero over the lifetime.
            float decay = 1.0f - (elapsed / (float) durationTicks);
            float amp = amplitude * decay;

            float yawOffset = random.nextFloat(-amp, amp);
            float pitchOffset = random.nextFloat(-amp, amp) * 0.5f;

            float yaw = baseYaw + yawOffset;
            // Keep pitch within the valid range and avoid gimbal flipping.
            float pitch = Math.max(-89.0f, Math.min(89.0f, basePitch + pitchOffset));
            player.setRotation(yaw, pitch);
        }

        /** Cancel the driver task without touching the map. */
        private void cancel() {
            if (task != null) {
                task.cancel();
                task = null;
            }
        }

        /** Cancel the driver and put the player back exactly once. */
        private void cancelAndRestore() {
            cancel();
            ACTIVE.remove(playerId, this);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline() && !player.isDead()) {
                player.setRotation(baseYaw, basePitch);
            }
        }
    }
}