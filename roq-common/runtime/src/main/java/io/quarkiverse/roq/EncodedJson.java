package io.quarkiverse.roq;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Wrapper around {@link JsonObject} or {@link JsonArray} that controls how data is serialized
 * by the Quarkus bytecode recorder.
 * <p>
 * Without this wrapper, the recorder recursively walks the Map/List structure, creating a constant pool
 * entry for every nested key and value. For large data (e.g., collections with many articles), this overflows
 * the JVM's 65,535 constant pool entry limit, causing {@code ClassTooLargeException}.
 * <p>
 * This wrapper serializes the data as chunked JSON strings via {@link RecordableConstructor}, collapsing
 * the entire object tree into a few opaque string constants instead of thousands of individual entries.
 */
public class EncodedJson {

    private static final int CHUNK_SIZE = 50_000;

    private final Object data;

    @RecordableConstructor
    public EncodedJson(boolean array, List<String> chunks) {
        String json = String.join("", chunks);
        this.data = array ? new JsonArray(json) : new JsonObject(json);
    }

    public EncodedJson(JsonObject data) {
        this.data = data;
    }

    public EncodedJson(JsonArray data) {
        this.data = data;
    }

    public boolean isArray() {
        return data instanceof JsonArray;
    }

    public List<String> getChunks() {
        String encoded = data instanceof JsonObject o ? o.encode() : ((JsonArray) data).encode();
        int numChunks = (encoded.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        List<String> chunks = new ArrayList<>(numChunks);
        for (int i = 0; i < encoded.length(); i += CHUNK_SIZE) {
            chunks.add(encoded.substring(i, Math.min(i + CHUNK_SIZE, encoded.length())));
        }
        return chunks;
    }

    @SuppressWarnings("unchecked")
    public <T> T get() {
        return (T) data;
    }

    public static EncodedJson of(Object data) {
        if (data instanceof JsonObject o) {
            return new EncodedJson(o);
        } else if (data instanceof JsonArray a) {
            return new EncodedJson(a);
        }
        throw new IllegalArgumentException("Unsupported data type: " + data.getClass());
    }
}
