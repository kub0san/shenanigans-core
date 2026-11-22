package com.hizeric.shenaniganscore.core.config;

import java.util.List;

/**
 * One managed configuration file. The registry lives in {@link ConfigAutoUpdater}.
 *
 * <p>{@code obsoleteKeys} is an explicit list of keys that are stripped from disk on the next
 * run. It must never be derived from "keys missing from the bundled defaults", or an admin's
 * custom keys would be silently deleted.</p>
 *
 * @param resourcePath bundled resource under {@code src/main/resources}
 * @param obsoleteKeys leaf keys (dot-separated) to null out; never null
 * @param version      {@code config-version} written to the file after an update
 */
public record ConfigSpec(String resourcePath, List<String> obsoleteKeys, int version) {

    public ConfigSpec {
        if (resourcePath == null || resourcePath.isBlank()) {
            throw new IllegalArgumentException("resourcePath must not be blank");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must not be negative: " + version);
        }
        obsoleteKeys = obsoleteKeys == null ? List.of() : List.copyOf(obsoleteKeys);
    }
}
