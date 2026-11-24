package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.network.PacketEventsBridge;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side body/equipment mask for the Rift Cleave dash window.
 *
 * <p>One instance owns one dash. It hides the caster from other clients by applying the invisible
 * flag and registering a short-lived PacketEvents listener that rewrites every
 * {@code ENTITY_EQUIPMENT} packet for the caster into an empty six-slot packet. Because the listener
 * intercepts the server's own authoritative updates, a hotbar switch during the dash cannot race
 * a per-tick resend and leave a stale item on a viewer's client. Viewers then see only the
 * {@link MannequinEchoRenderer} afterimages.</p>
 *
 * <p>On restore the listener is unregistered first (otherwise it would cancel the real restore
 * packet), then one equipment packet built from the caster's <em>current</em> inventory is sent to
 * every online player, and finally the original invisibility flag is restored. Every restore is
 * idempotent, and the static hooks cover death, logout and plugin disable when a timeline cannot
 * run its own {@code restore()}.</p>
 *
 * <p>All PacketEvents access goes through {@link PacketEventsBridge}; this class never calls
 * {@code setAPI}. The first-person view model is client-side and stays visible by design.</p>
 */
public final class DashVisibilityMask {

    /**
     * Six empty slots; a shared immutable list is safe because the wrapper only reads it.
     *
     * <p>Built on first use rather than at class load: {@code ItemStack.EMPTY} requires the live
     * plugin-owned PacketEvents singleton, and the pure viewer policy below must stay testable
     * without one.</p>
     */
    private static final class EmptyEquipment {
        private static final List<Equipment> SLOTS = List.of(
                new Equipment(EquipmentSlot.MAIN_HAND, ItemStack.EMPTY),
                new Equipment(EquipmentSlot.OFF_HAND, ItemStack.EMPTY),
                new Equipment(EquipmentSlot.BOOTS, ItemStack.EMPTY),
                new Equipment(EquipmentSlot.LEGGINGS, ItemStack.EMPTY),
                new Equipment(EquipmentSlot.CHEST_PLATE, ItemStack.EMPTY),
                new Equipment(EquipmentSlot.HELMET, ItemStack.EMPTY));
    }

    private static final Set<DashVisibilityMask> ACTIVE = ConcurrentHashMap.newKeySet();

    private final Player player;
    private final int entityId;
    private final boolean wasInvisible;
    private PacketListenerAbstract equipmentListener;
    private boolean closed;

    public DashVisibilityMask(Player player) {
        // Entry into the packet-backed feature: fail loudly if the hard dependency is missing.
        PacketEventsBridge.requireReady();
        this.player = player;
        // Hand off any earlier dash's original flag: a second cast (short cooldown or the
        // cooldown-bypassing debug command) must not inherit an invisibility it has to undo.
        this.wasInvisible = relinquish(player);
        this.entityId = player == null ? -1 : player.getEntityId();
        ACTIVE.add(this);
        if (player == null) return;
        player.setInvisible(true);
        registerEquipmentListener();
    }

    /** Retire an older mask for the same caster and return the flag it originally captured. */
    private static boolean relinquish(Player player) {
        boolean original = player != null && player.isInvisible();
        for (DashVisibilityMask mask : new ArrayList<>(ACTIVE)) {
            if (mask.player == null || player == null
                    || !mask.player.getUniqueId().equals(player.getUniqueId())) continue;
            original = mask.wasInvisible;
            mask.closed = true;
            ACTIVE.remove(mask);
            // The new mask takes over the client-side equipment hide, so the old listener must go
            // with it: it matches the same entity id and would otherwise mask packets forever.
            mask.unregisterEquipmentListener();
        }
        return original;
    }

    /**
     * Pure viewer policy for the packet mask: third parties must see the empty equipment packet,
     * while the caster keeps their own first-person equipment. Non-player viewers and packets for
     * other entities are never masked.
     */
    static boolean shouldMaskViewer(UUID casterUuid, int maskedEntityId, int packetEntityId,
                                    Object viewer) {
        if (viewer == null || !(viewer instanceof Player player)) return false;
        if (packetEntityId != maskedEntityId) return false;
        return !player.getUniqueId().equals(casterUuid);
    }

    /** Intercept every authoritative equipment update for this caster while the dash is live. */
    private void registerEquipmentListener() {
        if (closed || player == null || entityId < 0 || !PacketEventsBridge.isReady()) return;
        equipmentListener = new PacketListenerAbstract(PacketListenerPriority.HIGH) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.ENTITY_EQUIPMENT) return;
                final WrapperPlayServerEntityEquipment incoming;
                try {
                    incoming = new WrapperPlayServerEntityEquipment(event);
                } catch (Throwable t) {
                    // Unreadable packet: fail open so the viewer keeps a valid equipment state.
                    return;
                }
                if (incoming.getEntityId() != entityId) return;
                Object viewer = event.getPlayer();
                if (!shouldMaskViewer(player.getUniqueId(), entityId,
                        incoming.getEntityId(), viewer)) return;
                event.setCancelled(true);
                try {
                    // Silent send bypasses this listener; a regular send would be re-cancelled.
                    PacketEvents.getAPI().getPlayerManager().sendPacketSilently(
                            viewer, new WrapperPlayServerEntityEquipment(entityId, EmptyEquipment.SLOTS));
                } catch (Throwable t) {
                    log("Failed to mask dash equipment packet", t);
                }
            }
        };
        try {
            PacketEvents.getAPI().getEventManager().registerListener(equipmentListener);
        } catch (Throwable t) {
            equipmentListener = null;
            log("Failed to register dash equipment listener", t);
        }
    }

    /** Stop intercepting; safe to call more than once and during PacketEvents teardown. */
    private void unregisterEquipmentListener() {
        PacketListenerAbstract listener = equipmentListener;
        if (listener == null) return;
        equipmentListener = null;
        if (!PacketEventsBridge.isReady()) return;
        try {
            PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        } catch (Throwable t) {
            log("Failed to unregister dash equipment listener", t);
        }
    }

    /**
     * Re-apply the invisible flag for one timeline tick. This is a cheap Bukkit-state self-heal
     * only: equipment masking is entirely packet-listener driven, so no packets are sent here.
     */
    public void tick() {
        if (closed || player == null || !player.isOnline()) return;
        player.setInvisible(true);
    }

    /** Restore the invisibility flag and every viewer's real equipment. Idempotent. */
    public void restore() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(this);
        if (player == null) return;

        if (PacketEventsBridge.isReady()) {
            // Unregister first: a live listener would cancel the real packet we are about to send.
            unregisterEquipmentListener();
            List<Equipment> real = List.of(
                    new Equipment(EquipmentSlot.MAIN_HAND,
                            convert(player.getInventory().getItemInMainHand())),
                    new Equipment(EquipmentSlot.OFF_HAND,
                            convert(player.getInventory().getItemInOffHand())),
                    new Equipment(EquipmentSlot.BOOTS, convert(player.getInventory().getBoots())),
                    new Equipment(EquipmentSlot.LEGGINGS, convert(player.getInventory().getLeggings())),
                    new Equipment(EquipmentSlot.CHEST_PLATE,
                            convert(player.getInventory().getChestplate())),
                    new Equipment(EquipmentSlot.HELMET, convert(player.getInventory().getHelmet())));
            // Broadcast rather than resend only to the viewers seen during the dash: anyone out
            // of range, or who joined mid-dash, may still hold the empty mask on their client.
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                send(viewer, new WrapperPlayServerEntityEquipment(entityId, real));
            }
        }
        // Must happen even when PacketEvents is already disabled during plugin teardown.
        player.setInvisible(wasInvisible);
    }

    /** Immediate teardown for a dying or disconnecting caster (timeline cleanup would be a tick late). */
    public static void restore(Player player) {
        if (player == null) return;
        for (DashVisibilityMask mask : new ArrayList<>(ACTIVE)) {
            if (mask.player == null || !mask.player.getUniqueId().equals(player.getUniqueId())) continue;
            try {
                mask.restore();
            } catch (Throwable ignored) {
                // One bad mask must not abort death/quit handling or the rest of teardown.
            }
        }
    }

    /** PlugManX / reload safety net — call from the plugin's disable path before cancelTasks. */
    public static void shutdown() {
        for (DashVisibilityMask mask : new ArrayList<>(ACTIVE)) {
            try {
                mask.restore();
            } catch (Throwable ignored) {
                // Teardown must never abort other cleanup hooks.
            }
        }
    }

    private static ItemStack convert(org.bukkit.inventory.ItemStack bukkit) {
        return bukkit == null ? ItemStack.EMPTY : SpigotConversionUtil.fromBukkitItemStack(bukkit);
    }

    private static void send(Player viewer, PacketWrapper<?> packet) {
        if (viewer == null || !viewer.isOnline()) return;
        // Public entry points call requireReady(); teardown is defensive because PacketEvents may
        // already be disabled while the plugin is being unloaded.
        if (!PacketEventsBridge.isReady()) return;
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        } catch (Throwable t) {
            log("Failed to send dash visibility packet to " + viewer.getName(), t);
        }
    }

    private static void log(String message, Throwable t) {
        ShenanigansCore plugin = ShenanigansCore.getInstance();
        if (plugin != null) plugin.getLogger().warning(message + ": " + t.getMessage());
    }
}
