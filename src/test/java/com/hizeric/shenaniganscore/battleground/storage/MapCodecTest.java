package com.hizeric.shenaniganscore.battleground.storage;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The dependency-free map blob used for shop item data and player snapshots. */
class MapCodecTest {

    @Test
    void nestedMapsListsAndPrimitiveTypesRoundTrip() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("text", "blue");
        source.put("int", 7);
        source.put("long", 9L);
        source.put("double", 1.5d);
        source.put("float", 2.5f);
        source.put("short", (short) 4);
        source.put("byte", (byte) 3);
        source.put("bool", true);
        source.put("null", null);
        List<Object> nested = new ArrayList<>();
        nested.add("a");
        nested.add(Map.of("deep", 2));
        nested.add(false);
        source.put("list", nested);
        source.put("map", Map.of("child", List.of(1, 2, 3)));

        Map<String, Object> decoded = MapCodec.decode(MapCodec.encode(source));

        assertEquals(source, decoded);
    }

    @Test
    void anEmptyMapRoundTrips() {
        assertEquals(Map.of(), MapCodec.decode(MapCodec.encode(Map.of())));
    }

    @Test
    void corruptInputIsRejectedRatherThanSilentlyIgnored() {
        assertThrows(IllegalArgumentException.class, () -> MapCodec.decode("not base64 !!"));
    }
}
