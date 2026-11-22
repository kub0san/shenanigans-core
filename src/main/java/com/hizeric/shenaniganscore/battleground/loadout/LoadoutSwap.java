package com.hizeric.shenaniganscore.battleground.loadout;

public final class LoadoutSwap {

    public static final int SWORD_SLOT = 0;

    private LoadoutSwap() { }

    public static <T> T[] equipSword(T[] current, T sword) {
        T[] next = require(current).clone();
        next[SWORD_SLOT] = sword;
        return next;
    }

    public static <T> T[] reset(T[] serverKit) {
        return require(serverKit).clone();
    }

    public static <T> T[] snapshot(T[] live) {
        return require(live).clone();
    }

    private static <T> T[] require(T[] source) {
        if (source == null) {
            throw new IllegalArgumentException("source array cannot be null");
        }
        if (source.length <= SWORD_SLOT) {
            throw new IllegalArgumentException(
                    "source must have at least " + (SWORD_SLOT + 1) + " slots, got " + source.length);
        }
        return source;
    }
}