package com.hizeric.shenaniganscore.battleground.storage;

import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shop catalogue round-trips every row, including a custom item's map-based item data. */
class ShopRepositoryTest {

    private static List<ShopEntry> catalogue() {
        return List.of(
                ShopEntry.ability("blue", Material.END_ROD, "Blue", 40, 2, "jjk:blue"),
                ShopEntry.customItem("minecraft:dash_sword", Material.NETHERITE_SWORD,
                        "Dash Sword", 350, "minecraft:dash_sword",
                        Map.of("type", "NETHERITE_SWORD", "Unbreakable", 1)),
                ShopEntry.ultimate("purple", Material.AMETHYST_SHARD, "Purple", 800, 12, "jjk:purple")
        );
    }

    @Test
    void catalogueRoundTripsEveryField(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            ShopRepository repo = new ShopRepository(db);
            assertTrue(repo.load().isEmpty());

            List<ShopEntry> expected = catalogue();
            repo.save(expected);

            assertEquals(expected, repo.load());
        } finally {
            db.close();
        }
    }

    @Test
    void savingAnEmptyCatalogueClearsPreviousRows(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            ShopRepository repo = new ShopRepository(db);
            repo.save(catalogue());
            repo.save(List.of());

            assertTrue(repo.load().isEmpty());
        } finally {
            db.close();
        }
    }
}
