package com.hizeric.shenaniganscore.testsupport;

import com.hizeric.shenaniganscore.battleground.arena.ArenaRuntimeConfig;
import com.hizeric.shenaniganscore.battleground.arena.WorldEditBridge;
import com.hizeric.shenaniganscore.battleground.shop.ShopPolicyConfig;
import com.hizeric.shenaniganscore.core.config.AbilityTuningConfig;
import com.hizeric.shenaniganscore.core.domain.DomainRegistry;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Puts the mutable global holders back to their shipped baseline around every test, in one place.
 *
 * <p>Register with {@code @ExtendWith(GlobalStateExtension.class)} or {@code @RegisterExtension}.
 * The reset runs before each test and again afterwards, so a config load, a domain claim or a
 * WorldEdit toggle in one test cannot leak into the next. A test that needs a specific config
 * installs it in its own setup, after the extension has reset.</p>
 *
 * <p>Limits imposed by the supported APIs (no reflection): the tuning holders expose only
 * {@code load(File, Logger)} as a setter, so a previously installed non-default holder cannot be
 * restored by reference — they are reset to shipped defaults. {@code ArenaEntryGuard} has no bulk
 * token clear; its tokens are TTL-bound and each test clears its own via {@code clearTransit}.</p>
 */
public final class GlobalStateExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) {
        resetToDefaults();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        resetToDefaults();
    }

    /** Restores every mutable global holder that has a supported reset path. */
    public static void resetToDefaults() {
        AbilityTuningConfig.load(null, null);
        ArenaRuntimeConfig.load(null, null);
        ShopPolicyConfig.apply(ShopPolicyConfig.defaults().killstreakRequirements());
        DomainRegistry.get().clearAll();
        WorldEditBridge.setEnabled(true);
        WorldEditBridge.clearCachedFormat();
    }
}
