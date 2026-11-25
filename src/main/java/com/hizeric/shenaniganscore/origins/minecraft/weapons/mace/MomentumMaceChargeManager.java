package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.hizeric.shenaniganscore.ShenanigansCore;

public final class MomentumMaceChargeManager {
    private static final int MAX = 3;
    private static final long RECHARGE_MS = 10000L;
    private static final NamespacedKey CHARGES = new NamespacedKey("shenaniganscore", "momentum_mace_charges");
    private static final NamespacedKey NEXT = new NamespacedKey("shenaniganscore", "momentum_mace_next_recharge");

    private MomentumMaceChargeManager() { }

    public static int getCharges(Player p) { refresh(p); return readCharges(p); }
    public static boolean hasCharge(Player p) { return getCharges(p) > 0; }

    public static void refresh(Player p) {
        PersistentDataContainer c = p.getPersistentDataContainer();
        int charges = c.getOrDefault(CHARGES, PersistentDataType.INTEGER, MAX);
        if (charges < 0) charges = 0;
        if (charges > MAX) charges = MAX;
        long now = System.currentTimeMillis();
        long next = c.getOrDefault(NEXT, PersistentDataType.LONG, 0L);
        if (charges >= MAX) { c.set(CHARGES, PersistentDataType.INTEGER, charges); c.remove(NEXT); return; }
        if (next == 0L) { next = now + RECHARGE_MS; c.set(NEXT, PersistentDataType.LONG, next); }
        if (now >= next) {
            long elapsed = now - next;
            int gained = (int)(elapsed / RECHARGE_MS) + 1;
            charges = Math.min(MAX, charges + gained);
            if (charges >= MAX) { c.set(CHARGES, PersistentDataType.INTEGER, charges); c.remove(NEXT); return; }
            long remainder = elapsed % RECHARGE_MS;
            next = now - remainder + RECHARGE_MS;
            c.set(CHARGES, PersistentDataType.INTEGER, charges);
            c.set(NEXT, PersistentDataType.LONG, next);
        } else {
            c.set(CHARGES, PersistentDataType.INTEGER, charges);
        }
    }

    private static int readCharges(Player p) {
        return p.getPersistentDataContainer().getOrDefault(CHARGES, PersistentDataType.INTEGER, MAX);
    }

    public static boolean consumeCharge(Player p) {
        refresh(p);
        int c = readCharges(p);
        if (c <= 0) return false;
        p.getPersistentDataContainer().set(CHARGES, PersistentDataType.INTEGER, c - 1);
        if (c - 1 < MAX) {
            long next = p.getPersistentDataContainer().getOrDefault(NEXT, PersistentDataType.LONG, 0L);
            if (next == 0L) p.getPersistentDataContainer().set(NEXT, PersistentDataType.LONG, System.currentTimeMillis() + RECHARGE_MS);
        }
        return true;
    }
}