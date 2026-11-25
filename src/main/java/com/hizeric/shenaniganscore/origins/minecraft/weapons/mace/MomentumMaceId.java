package com.hizeric.shenaniganscore.origins.minecraft.weapons.mace;

public final class MomentumMaceId {
    public static final String NEW_ID = "minecraft:momentum_mace";
    private MomentumMaceId() { }
    public static String migrate(String id) { return NEW_ID.equals(id) ? NEW_ID : id; }
}