package com.hizeric.shenaniganscore.battleground.loadout;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Base64;

/**
 * Versioned Base64 wrapper around Bukkit's item-array serialisation.
 *
 * <p>Format v2 is the string prefix {@code LK2:} followed by Base64 of the serialized byte array.
 * Anything without the prefix is treated as legacy v1 (raw Base64 of the serialized bytes), so
 * existing saves keep loading. A failure is reported as a typed result instead of an empty string,
 * so a caller can distinguish "empty save" from "encode failed" instead of silently truncating.</p>
 */
public final class LoadoutCodec {

    public static final String VERSION_PREFIX = "LK2:";

    private static final NamespacedKey LEGACY_ABILITY =
            NamespacedKey.fromString("shenaniganscore:ability");

    public record EncodeResult(String data, String error) {
        public boolean success() { return error == null; }
    }

    public record DecodeResult(ItemStack[] items, boolean migratedFromLegacy, String error) {
        public boolean success() { return error == null; }
    }

    private LoadoutCodec() { }

    public static EncodeResult encode(ItemStack[] items) {
        if (items == null) return new EncodeResult(null, "items cannot be null");
        try {
            byte[] serialized = ItemStack.serializeItemsAsBytes(items);
            String b64 = Base64.getEncoder().encodeToString(serialized);
            return new EncodeResult(VERSION_PREFIX + b64, null);
        } catch (RuntimeException e) {
            return new EncodeResult(null, "encode failed: " + e.getMessage());
        }
    }

    public static DecodeResult decode(String data, ItemStack[] fallback) {
        if (data == null || data.isBlank()) {
            return new DecodeResult(fallback, false, "empty");
        }
        boolean legacy = !data.startsWith(VERSION_PREFIX);
        String b64 = legacy ? data : data.substring(VERSION_PREFIX.length());
        try {
            byte[] bytes = Base64.getDecoder().decode(b64);
            ItemStack[] items = ItemStack.deserializeItemsFromBytes(bytes);
            if (items == null || items.length != fallback.length) {
                return new DecodeResult(fallback, legacy,
                        "size mismatch: got " + (items == null ? "null" : items.length)
                                + ", expected " + fallback.length);
            }
            int scrubbed = scrubLegacyTechniques(items);
            return new DecodeResult(items, legacy,
                    scrubbed > 0 ? "scrubbed " + scrubbed + " legacy technique item(s)" : null);
        } catch (RuntimeException e) {
            return new DecodeResult(fallback, legacy, "decode failed: " + e.getMessage());
        }
    }

    private static int scrubLegacyTechniques(ItemStack[] items) {
        if (LEGACY_ABILITY == null) return 0;
        int scrubbed = 0;
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer()
                    .get(LEGACY_ABILITY, PersistentDataType.STRING);
            if (id != null && !id.isBlank()) {
                items[i] = null;
                scrubbed++;
            }
        }
        return scrubbed;
    }
}