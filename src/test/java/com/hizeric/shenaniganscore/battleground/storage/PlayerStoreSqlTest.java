package com.hizeric.shenaniganscore.battleground.storage;

import com.hizeric.shenaniganscore.battleground.player.BattlegroundPlayer;
import com.hizeric.shenaniganscore.battleground.player.PlayerStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The store seam: a cache miss reads from SQL, and shutdown performs a synchronous flush through
 * the same repository (no Bukkit scheduler involved when the store was never enabled).
 */
class PlayerStoreSqlTest {

    @Test
    void getLoadsFromSqlAndDisableFlushesTheDirtyRecord(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            UUID uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");
            new PlayerRepository(db).upsert(new PlayerRecord(uuid, 55, 4, 2, 1, 6,
                    List.of("jjk_blue"), List.of("jjk:blue", "", ""), "layout-one"));

            PlayerStore store = new PlayerStore(null, temp.resolve("bg").toFile(), db);
            BattlegroundPlayer player = store.get(uuid);
            assertEquals(55, player.credits());
            assertEquals(4, player.kills());
            assertEquals(2, player.deaths());
            assertEquals(1, player.streak());
            assertEquals(6, player.bestStreak());
            assertTrue(player.owns("jjk_blue"));
            assertEquals("jjk:blue", player.rail().get(0).toString());
            assertEquals("layout-one", player.layoutData());

            player.setCredits(77);
            store.markDirty(uuid);
            store.disable();

            PlayerRecord flushed = new PlayerRepository(db).load(uuid).orElseThrow();
            assertEquals(77, flushed.credits());
            assertEquals("layout-one", flushed.layout());
        } finally {
            db.close();
        }
    }

    @Test
    void anUnknownUuidYieldsABlankRecord(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            PlayerStore store = new PlayerStore(null, temp.resolve("bg").toFile(), db);
            BattlegroundPlayer player = store.get(UUID.randomUUID());

            assertEquals(0, player.credits());
            assertEquals("", player.layoutData());
        } finally {
            db.close();
        }
    }
}
