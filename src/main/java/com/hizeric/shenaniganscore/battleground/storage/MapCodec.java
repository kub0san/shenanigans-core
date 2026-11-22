package com.hizeric.shenaniganscore.battleground.storage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static com.hizeric.shenaniganscore.battleground.storage.CodecUtil.stringMap;

/**
 * Tiny, dependency-free binary encoding for the {@code Map<String, Object>} blobs stored in SQL
 * (shop item data and player snapshots). Only primitives, strings, lists and maps are supported —
 * no arbitrary object deserialisation, so a corrupt row cannot instantiate a gadget.
 *
 * <p>The first byte is a format version so the encoding can evolve without breaking readers of
 * older blobs.</p>
 */
final class MapCodec {

    /** Bump only when the wire format changes in a backwards-incompatible way. */
    private static final byte FORMAT_VERSION = 1;

    private static final byte NULL = 0;
    private static final byte STRING = 1;
    private static final byte INT = 2;
    private static final byte LONG = 3;
    private static final byte DOUBLE = 4;
    private static final byte BOOLEAN = 5;
    private static final byte BYTE = 6;
    private static final byte SHORT = 7;
    private static final byte FLOAT = 8;
    private static final byte CHAR = 9;
    private static final byte LIST = 10;
    private static final byte MAP = 11;

    /** Maximum nesting depth accepted on read or write; guards against stack overflow on corrupt blobs. */
    private static final int MAX_DEPTH = 32;

    private MapCodec() { }

    static String encode(Map<String, Object> value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(FORMAT_VERSION);
            writeMap(out, value, 0);
        } catch (IOException e) {
            throw new StorageException("Could not encode a storage blob", e);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
    }

    static Map<String, Object> decode(String encoded) {
        if (encoded == null || encoded.isBlank()) return Map.of();
        try {
            byte[] raw = Base64.getUrlDecoder().decode(encoded);
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(raw));
            byte version = in.readByte();
            if (version != FORMAT_VERSION) {
                throw new IOException("Unsupported storage blob version: " + version);
            }
            Object value = readValue(in, 0);
            if (!(value instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Storage blob is not a map");
            }
            return stringMap(map);
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Corrupt storage blob", e);
        }
    }

    private static void writeMap(DataOutputStream out, Map<String, Object> map, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Storage blob exceeds maximum nesting depth");
        out.writeByte(MAP);
        out.writeInt(map.size());
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            writeString(out, entry.getKey());
            writeValue(out, entry.getValue(), depth + 1);
        }
    }

    private static void writeValue(DataOutputStream out, Object value, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Storage blob exceeds maximum nesting depth");
        if (value == null) {
            out.writeByte(NULL);
        } else if (value instanceof String text) {
            out.writeByte(STRING);
            writeString(out, text);
        } else if (value instanceof Integer number) {
            out.writeByte(INT);
            out.writeInt(number);
        } else if (value instanceof Long number) {
            out.writeByte(LONG);
            out.writeLong(number);
        } else if (value instanceof Double number) {
            out.writeByte(DOUBLE);
            out.writeDouble(number);
        } else if (value instanceof Boolean flag) {
            out.writeByte(BOOLEAN);
            out.writeBoolean(flag);
        } else if (value instanceof Byte number) {
            out.writeByte(BYTE);
            out.writeByte(number);
        } else if (value instanceof Short number) {
            out.writeByte(SHORT);
            out.writeShort(number);
        } else if (value instanceof Float number) {
            out.writeByte(FLOAT);
            out.writeFloat(number);
        } else if (value instanceof Character character) {
            out.writeByte(CHAR);
            out.writeChar(character);
        } else if (value instanceof Map<?, ?> map) {
            Map<String, Object> stringKeys = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                stringKeys.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            writeMap(out, stringKeys, depth);
        } else if (value instanceof List<?> list) {
            out.writeByte(LIST);
            out.writeInt(list.size());
            for (Object item : list) writeValue(out, item, depth + 1);
        } else if (value instanceof Collection<?> collection) {
            throw new IOException("Unsupported storage collection type: " + collection.getClass().getName());
        } else {
            throw new IOException("Unsupported storage value type: " + value.getClass().getName());
        }
    }

    private static void writeString(DataOutputStream out, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static Object readValue(DataInputStream in, int depth) throws IOException {
        if (depth > MAX_DEPTH) throw new IOException("Storage blob exceeds maximum nesting depth");
        byte tag = in.readByte();
        return switch (tag) {
            case NULL -> null;
            case STRING -> readString(in);
            case INT -> in.readInt();
            case LONG -> in.readLong();
            case DOUBLE -> in.readDouble();
            case BOOLEAN -> in.readBoolean();
            case BYTE -> in.readByte();
            case SHORT -> in.readShort();
            case FLOAT -> in.readFloat();
            case CHAR -> in.readChar();
            case LIST -> {
                int size = nonNegative(in.readInt());
                List<Object> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) list.add(readValue(in, depth + 1));
                yield list;
            }
            case MAP -> {
                int size = nonNegative(in.readInt());
                Map<String, Object> map = new LinkedHashMap<>();
                for (int i = 0; i < size; i++) map.put(readString(in), readValue(in, depth + 1));
                yield map;
            }
            default -> throw new IOException("Unknown storage value tag: " + tag);
        };
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = nonNegative(in.readInt());
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new IOException("Truncated storage string");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int nonNegative(int size) throws IOException {
        if (size < 0 || size > 1_000_000) throw new IOException("Implausible storage collection size");
        return size;
    }
}