package com.hizeric.shenaniganscore.battleground.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player records upsert as one row and read back every field (credits, stats, rail, layout). */
class PlayerRepositoryTest {

    private static final UUID UUID_ONE = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static PlayerRecord record(UUID uuid, int credits) {
        return new PlayerRecord(uuid, credits, 7, 3, 2, 9,
                List.of("jjk_blue", "minecraft:dash_sword"),
                List.of("jjk:blue", "", "jjk:purple"), "base64-layout");
    }

    @Test
    void upsertThenLoadRestoresEveryField(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            PlayerRepository repo = new PlayerRepository(db);
            assertTrue(repo.load(UUID_ONE).isEmpty());

            PlayerRecord expected = record(UUID_ONE, 120);
            repo.upsert(expected);

            assertEquals(expected, repo.load(UUID_ONE).orElseThrow());
        } finally {
            db.close();
        }
    }

    @Test
    void aSecondUpsertOverwritesWithoutCreatingADuplicateRow(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try {
            PlayerRepository repo = new PlayerRepository(db);
            repo.upsert(record(UUID_ONE, 120));

            PlayerRecord changed = new PlayerRecord(UUID_ONE, 0, 8, 4, 0, 9,
                    List.of(), List.of("", "", ""), "");
            repo.upsert(changed);

            assertEquals(changed, repo.load(UUID_ONE).orElseThrow());
            try (Connection connection = db.connection();
                 Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM players")) {
                assertTrue(result.next());
                assertEquals(1, result.getInt(1));
            }
        } finally {
            db.close();
        }
    }
}
