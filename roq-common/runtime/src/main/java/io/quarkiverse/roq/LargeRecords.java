package io.quarkiverse.roq;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Serializes objects to byte arrays at build time (for {@code GeneratedResourceBuildItem})
 * and deserializes them from classpath resources at runtime.
 * <p>
 * This avoids the JVM's 65,535 constant pool entry limit that the bytecode recorder
 * hits on large sites by storing serialized data in resource files instead of bytecode constants.
 */
public final class LargeRecords {

    private LargeRecords() {
    }

    /**
     * Serialize an object to a byte array for use with {@code GeneratedResourceBuildItem}.
     */
    public static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to serialize for resource recording", e);
        }
    }

    /**
     * Serialize a {@link JsonObject} or {@link JsonArray} by converting to plain Java types
     * (Map/List), which are fully serializable.
     */
    public static byte[] serializeJson(Object jsonData) {
        if (jsonData instanceof JsonObject obj) {
            return serialize(toSerializable(obj));
        } else if (jsonData instanceof JsonArray arr) {
            return serialize(toSerializable(arr));
        }
        throw new IllegalArgumentException("Expected JsonObject or JsonArray, got: " + jsonData.getClass());
    }

    /**
     * Deserialize a {@link JsonObject} or {@link JsonArray} previously written with {@link #serializeJson}.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserializeJson(String resourcePath) {
        Object data = deserialize(resourcePath);
        if (data instanceof Map<?, ?> map) {
            return (T) new JsonObject((Map<String, Object>) map);
        }
        return (T) new JsonArray((List<Object>) data);
    }

    /**
     * Convert a {@link JsonObject} to a serializable {@link Map}, replacing any nested
     * {@link JsonObject}/{@link JsonArray} values with plain Map/List.
     */
    public static Map<String, Object> toSerializable(JsonObject json) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (var entry : json) {
            map.put(entry.getKey(), toSerializableValue(entry.getValue()));
        }
        return map;
    }

    public static List<Object> toSerializable(JsonArray json) {
        List<Object> list = new ArrayList<>(json.size());
        for (Object item : json) {
            list.add(toSerializableValue(item));
        }
        return list;
    }

    private static Object toSerializableValue(Object value) {
        if (value instanceof JsonObject jo) {
            return toSerializable(jo);
        } else if (value instanceof JsonArray ja) {
            return toSerializable(ja);
        }
        return value;
    }

    /**
     * Deserialize an object from a classpath resource.
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(String resourcePath) {
        try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Resource not found: " + resourcePath);
            }
            try (ObjectInputStream ois = new ObjectInputStream(is)) {
                return (T) ois.readObject();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to deserialize resource: " + resourcePath, e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to deserialize resource: " + resourcePath, e);
        }
    }
}
