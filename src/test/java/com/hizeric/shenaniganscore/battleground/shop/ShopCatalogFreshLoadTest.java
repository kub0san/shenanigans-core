package com.hizeric.shenaniganscore.battleground.shop;

import com.hizeric.shenaniganscore.battleground.storage.Database;
import com.hizeric.shenaniganscore.core.ability.AbilityRegistry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: a fresh database has no stored rows, so load() seeds the catalogue and then merges
 * the built-in Dash Sword item into it. The seeded list must be mutable or that merge throws
 * UnsupportedOperationException and the whole plugin fails to enable.
 */
class ShopCatalogFreshLoadTest {

    private static ShopEntry dashSwordSeed() {
        return ShopEntry.customItem("minecraft:dash_sword", Material.NETHERITE_SWORD,
                "Dash Sword", 350, "minecraft:dash_sword", Map.of("type", "NETHERITE_SWORD"));
    }

    @Test
    void loadingAFreshCatalogueCanMergeTheBuiltInItem(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            ShopCatalog catalog = new ShopCatalog(null, db);
            AbilityRegistry registry = new AbilityRegistry(new ArrayList<>()::add);

            // Injected seed keeps the test server-free; the production seed needs a Bukkit ItemStack.
            assertDoesNotThrow(() -> catalog.load(registry, List.of(dashSwordSeed())));

            assertEquals(1, catalog.entries().size());
            assertTrue(catalog.entries().get(0).isCustomItem(),
                    "the built-in item must reach a fresh catalogue");
        } finally {
            db.close();
        }
    }
}
