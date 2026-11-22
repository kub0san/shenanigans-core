package com.hizeric.shenaniganscore.battleground.storage;

import com.hizeric.shenaniganscore.battleground.shop.ShopEntry;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.number;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.string;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.strings;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.stringMap;

/** Persists the whole catalogue atomically, preserving row order via an ordinal column. */
public final class ShopRepository {

    private final Database database;

    public ShopRepository(Database database) {
        this.database = Objects.requireNonNull(database, "database");
    }

    private static final Logger LOGGER = Logger.getLogger(ShopRepository.class.getName());

    public List<ShopEntry> load() {
        try (Connection connection = database.connection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT data FROM shop_entries ORDER BY ordinal ASC")) {
            List<ShopEntry> entries = new ArrayList<>();
            while (result.next()) {
                try {
                    entries.add(fromBlob(MapCodec.decode(result.getString("data"))));
                } catch (IllegalArgumentException e) {
                    LOGGER.warning("Skipping corrupt shop row: " + e.getMessage());
                }
            }
            return entries;
        } catch (SQLException e) {
            throw new StorageException("Could not load the shop catalogue", e);
        }
    }

    public void save(List<ShopEntry> entries) {
        try (Connection connection = database.connection()) {
            connection.setAutoCommit(false);
            try {
                try (Statement clear = connection.createStatement()) {
                    clear.executeUpdate("DELETE FROM shop_entries");
                }
                int ordinal = 0;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO shop_entries(ordinal, id, data) VALUES(?, ?, ?)")) {
                    for (ShopEntry entry : entries) {
                        insert.setInt(1, ordinal++);
                        insert.setString(2, entry.id());
                        insert.setString(3, toBlob(entry));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new StorageException("Could not save the shop catalogue", e);
        }
    }

    private static String toBlob(ShopEntry entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entry.id());
        data.put("category", entry.category().name());
        data.put("tags", new ArrayList<>(entry.tags()));
        data.put("icon", entry.icon().name());
        data.put("name", entry.displayName());
        data.put("price", entry.price());
        data.put("enabled", entry.enabled());
        data.put("ability-id", entry.abilityId());
        data.put("item-data", new LinkedHashMap<>(entry.itemData()));
        data.put("streak-required", entry.streakRequired());
        return MapCodec.encode(data);
    }

    private static ShopEntry fromBlob(Map<String, Object> data) {
        ShopEntry.Category category = ShopEntry.parseCategory(string(data.get("category")));
        if (category == null) category = ShopEntry.Category.ITEM;
        Material icon;
        try {
            icon = Material.valueOf(string(data.get("icon")));
        } catch (IllegalArgumentException e) {
            icon = Material.STONE;
        }
        Object itemData = data.get("item-data");
        Map<String, Object> itemMap = itemData instanceof Map<?, ?> map
                ? stringMap(map) : Map.of();
        return new ShopEntry(
                string(data.get("id")),
                category,
                strings(data.get("tags")),
                icon,
                string(data.get("name")),
                number(data.get("price")),
                Boolean.TRUE.equals(data.get("enabled")),
                data.get("ability-id") == null ? null : string(data.get("ability-id")),
                itemMap,
                number(data.get("streak-required")));
    }

}
