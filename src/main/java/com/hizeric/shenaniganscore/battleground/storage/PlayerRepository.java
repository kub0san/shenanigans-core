package com.hizeric.shenaniganscore.battleground.storage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.number;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.string;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.strings;

/** One row per player; the full snapshot is a {@link MapCodec} blob so fields can grow freely. */
public final class PlayerRepository {

    private final Database database;

    public PlayerRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    public Optional<PlayerRecord> load(UUID uuid) {
        if (uuid == null) return Optional.empty();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT uuid, data FROM players WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                String columnUuid = result.getString("uuid");
                try {
                    return Optional.of(fromBlob(columnUuid, MapCodec.decode(result.getString("data"))));
                } catch (IllegalArgumentException e) {
                    throw new StorageException("Could not load player " + uuid, e);
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load player " + uuid, e);
        }
    }

    public void upsert(PlayerRecord record) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("credits", record.credits());
        data.put("kills", record.kills());
        data.put("deaths", record.deaths());
        data.put("streak", record.streak());
        data.put("best-streak", record.bestStreak());
        data.put("owned", new ArrayList<>(record.owned()));
        data.put("rail", new ArrayList<>(record.rail()));
        data.put("layout", record.layout());
        String blob = MapCodec.encode(data);

        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(
                     "REPLACE INTO players(uuid, data) VALUES(?, ?)")) {
            statement.setString(1, record.uuid().toString());
            statement.setString(2, blob);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save player " + record.uuid(), e);
        }
    }

    private static PlayerRecord fromBlob(String columnUuid, Map<String, Object> data) {
        String blobUuid = string(data.get("uuid"));
        if (!blobUuid.isEmpty() && !blobUuid.equals(columnUuid)) {
            throw new StorageException("Player record uuid mismatch: column=" + columnUuid
                    + " blob=" + blobUuid);
        }
        UUID resolved = UUID.fromString(columnUuid);
        return new PlayerRecord(
                resolved,
                number(data.get("credits")),
                number(data.get("kills")),
                number(data.get("deaths")),
                number(data.get("streak")),
                number(data.get("best-streak")),
                strings(data.get("owned")),
                strings(data.get("rail")),
                string(data.get("layout")));
    }
}