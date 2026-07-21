package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.List;

import io.quarkus.runtime.annotations.RecordableConstructor;
import io.vertx.core.json.JsonObject;

/**
 * Wrapper around {@link JsonObject} that controls how data is serialized by the Quarkus bytecode recorder.
 * <p>
 * Without this wrapper, the recorder recursively walks the JsonObject's Map structure, creating a constant pool
 * entry for every nested key and value. For large data (e.g., collections with many articles), this overflows
 * the JVM's 65,535 constant pool entry limit, causing {@code ClassTooLargeException}.
 * <p>
 * This wrapper serializes the data as chunked JSON strings via {@link RecordableConstructor}, collapsing
 * the entire object tree into a few opaque string constants instead of thousands of individual entries.
 */
public class EncodedJsonObject {

    private static final int CHUNK_SIZE = 50_000;

    private final JsonObject data;

    @RecordableConstructor
    public EncodedJsonObject(List<String> chunks) {
        this.data = new JsonObject(String.join("", chunks));
    }

    public EncodedJsonObject(JsonObject data) {
        this.data = data;
    }

    public List<String> getChunks() {
        String encoded = data.encode();
        int numChunks = (encoded.length() + CHUNK_SIZE - 1) / CHUNK_SIZE;
        List<String> chunks = new ArrayList<>(numChunks);
        for (int i = 0; i < encoded.length(); i += CHUNK_SIZE) {
            chunks.add(encoded.substring(i, Math.min(i + CHUNK_SIZE, encoded.length())));
        }
        return chunks;
    }

    public JsonObject get() {
        return data;
    }
}
