package com.hizeric.shenaniganscore.battleground.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SQL spine: opening a fresh database installs a single schema_version row and all tables
 * idempotently, an unusable path fails loudly, and close is final + idempotent.
 */
class DatabaseTest {

    @Test
    void schemaVersionIsInitialisedOnceAndSurvivesReopen(@TempDir Path temp) throws Exception {
        File file = temp.resolve("database.db").toFile();

        Database first = Database.open(file);
        assertEquals(1, versionRows(first));
        assertEquals(Database.SCHEMA_VERSION, versionValue(first));
        first.close();

        Database reopened = Database.open(file);
        assertEquals(1, versionRows(reopened), "reopening must not add another version row");
        assertEquals(Database.SCHEMA_VERSION, versionValue(reopened));
        assertTrue(tableExists(reopened, "kits"));
        assertTrue(tableExists(reopened, "players"));
        assertTrue(tableExists(reopened, "shop_entries"));
        reopened.close();
    }

    @Test
    void openFailsLoudlyWhenTheDatabaseCannotBeCreated(@TempDir Path temp) throws Exception {
        File file = temp.resolve("database.db").toFile();
        assertTrue(file.mkdir(), "a directory at the db path makes SQLite fail to open it");

        assertThrows(DatabaseException.class, () -> Database.open(file));
    }

    @Test
    void closeIsIdempotentAndBlocksFurtherUse(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        db.close();

        assertDoesNotThrow(db::close, "close must be safe to call twice");
        assertThrows(SQLException.class, () -> db.connection());
    }

    @Test
    void backupWritesAReadableVacuumCopy(@TempDir Path temp) throws Exception {
        Database db = Database.open(temp.resolve("database.db").toFile());
        try (Connection connection = db.connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE probe(value TEXT)");
            statement.executeUpdate("INSERT INTO probe(value) VALUES('kept')");
        }

        File target = temp.resolve("backup.db").toFile();
        db.backupTo(target);
        assertTrue(target.exists());

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target.getAbsolutePath());
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT value FROM probe")) {
            assertTrue(result.next());
            assertEquals("kept", result.getString(1));
        }
        db.close();
    }

    private static int versionRows(Database db) throws SQLException {
        try (Connection connection = db.connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_version")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static int versionValue(Database db) throws SQLException {
        try (Connection connection = db.connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT version FROM schema_version")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static boolean tableExists(Database db, String table) throws SQLException {
        try (Connection connection = db.connection(); Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            assertTrue(result.next());
            return result.getInt(1) == 1;
        }
    }

    @Test
    void currentSchemaVersionIsPinned() {
        assertFalse(Database.SCHEMA_VERSION < 1);
        assertEquals(1, Database.SCHEMA_VERSION);
    }
}
