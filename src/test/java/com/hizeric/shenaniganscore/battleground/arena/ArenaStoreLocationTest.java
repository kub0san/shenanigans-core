package com.hizeric.shenaniganscore.battleground.arena;

import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ArenaStoreLocationTest {

    private static Map<String, Object> map() {
        return ArenaStore.locationMap(new Location(null, 1.5, 64.0, -2.5, 45f, -10f));
    }

    @Test
    void yawAndPitchAreStoredAsDoubles() {
        Map<String, Object> values = map();
        assertInstanceOf(Double.class, values.get("yaw"));
        assertInstanceOf(Double.class, values.get("pitch"));
        assertFalse(values.containsValue(45f), "Configurate rejects Float scalars");
    }

    @Test
    void theMapCanBeWrittenToAConfigurateNode() throws Exception {
        CommentedConfigurationNode node = CommentedConfigurationNode.root();
        node.set(map());
        assertEquals(45f, node.node("yaw").getFloat());
        assertEquals(-10f, node.node("pitch").getFloat());
        assertEquals(1.5, node.node("x").getDouble());
    }
}
