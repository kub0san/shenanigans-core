package com.hizeric.shenaniganscore.origins.minecraft.weapons.dashsword;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.hizeric.shenaniganscore.ShenanigansCore;
import com.hizeric.shenaniganscore.core.network.PacketEventsBridge;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side body/equipment mask for the Rift Cleave dash window.
 *
 * <p>One instance owns one dash. It hides the caster from other clients by applying the invisible
 * flag and sending an empty six-slot equipment packet every tick, so viewers see only the
 * {@link MannequinEchoRenderer} afterimages. Nothing here touches a potion effect or the Bukkit
 * inventory: every ability gate reads the real server-side held item.</p>
 *
 * <p>The equipment packet must be resent every tick — any legitimate inventory update overwrites
 * the client-side mask. Restore broadcasts the caster's <em>current</em> real stacks to every online
 * player, so a viewer who was outside the sync radius or joined late cannot keep a stale empty
 * state. Every restore is idempotent, and the static hooks cover death, logout and plugin disable
 * when a timeline cannot run its own {@code clear()}.</p>
 *
 * <p>All PacketEvents access goes through {@link PacketEventsBridge}; this class never calls
 * {@code setAPI}. The first-person view model is client-side and stays visible by design.</p>
 */
public final class DashVisibilityMask {

    private static final int VIEW_RADIUS = 48;
    private static final Set<DashVisibilityMask> ACTIVE = ConcurrentHashMap.newKeySet();

    /** Six empty slots; a shared immutable list is safe because the wrapper only reads it. */
    private static final List<Equipment> EMPTY_EQUIPMENT = List.of(
            new Equipment(EquipmentSlot.MAIN_HAND, ItemStack.EMPTY),
            new Equipment(EquipmentSlot.OFF_HAND, ItemStack.EMPTY),
            new Equipment(EquipmentSlot.BOOTS, ItemStack.EMPTY),
            new Equipment(EquipmentSlot.LEGGINGS, ItemStack.EMPTY),
            new Equipment(EquipmentSlot.CHEST_PLATE, ItemStack.EMPTY),
            new Equipment(EquipmentSlot.HELMET, ItemStack.EMPTY));

    private final Player player;
    private final boolean wasInvisible;
    private boolean closed;

    public DashVisibilityMask(Player player) {
        // Entry into the packet-backed feature: fail loudly if the hard dependency is missing.
        PacketEventsBridge.requireReady();
        this.player = player;
        // Hand off any earlier dash's original flag: a second cast (short cooldown or the
        // cooldown-bypassing debug command) must not inherit an invisibility it has to undo.
        this.wasInvisible = relinquish(player);
        ACTIVE.add(this);
        if (player != null) player.setInvisible(true);
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
            // No packet restore here: the new mask keeps the client-side equipment hidden, and
            // the old timeline's clear() now becomes a safe no-op.
        }
        return original;
    }

    /** Re-apply the hide for one timeline tick. */
    public void tick() {
        if (closed || player == null || !player.isOnline()) return;
        // Re-apply unconditionally: this is Bukkit state, not PacketEvents state.
        player.setInvisible(true);
        if (!PacketEventsBridge.isReady()) return;

        Location here = player.getLocation();
        double radiusSq = VIEW_RADIUS * VIEW_RADIUS;
        send(player, new WrapperPlayServerEntityEquipment(player.getEntityId(), EMPTY_EQUIPMENT));
        // Same viewer selection as MannequinEchoRenderer: caster plus same-world players close
        // enough to actually see the echo trail. A late entrant is covered on the next tick.
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.getUniqueId().equals(player.getUniqueId())) continue;
            if (viewer.getLocation().distanceSquared(here) <= radiusSq) {
                send(viewer, new WrapperPlayServerEntityEquipment(player.getEntityId(), EMPTY_EQUIPMENT));
            }
        }
    }

    /** Restore the invisibility flag and every viewer's real equipment. Idempotent. */
    public void restore() {
        if (closed) return;
        closed = true;
        ACTIVE.remove(this);
        if (player == null) return;

        // Must happen even when PacketEvents is already disabled during plugin teardown.
        player.setInvisible(wasInvisible);
        if (!PacketEventsBridge.isReady()) return;

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
        // Broadcast rather than resend only to the tick viewers: anyone out of range, or who
        // joined mid-dash, may still hold the empty mask on their client.
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            send(viewer, new WrapperPlayServerEntityEquipment(player.getEntityId(), real));
        }
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
            ShenanigansCore plugin = ShenanigansCore.getInstance();
            if (plugin != null) {
                plugin.getLogger().warning("Failed to send dash visibility packet to "
                        + viewer.getName() + ": " + t.getMessage());
            }
        }
    }
}
