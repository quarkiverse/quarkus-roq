package io.quarkiverse.roq;

import java.nio.charset.StandardCharsets;
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

    /**
     * Maximum chunk size in UTF-8 bytes. The JVM constant pool limit is 65,535 bytes for UTF-8 strings.
     * We use 30,000 bytes to account for multibyte characters and provide a safety margin.
     * See https://github.com/quarkiverse/quarkus-roq/issues/1133
     */
    private static final int CHUNK_SIZE_BYTES = 30_000;

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

        // Cheap lower bound: if even every character were 4 bytes it would still fit, skip the file-based chunking and encoding thing
        if (encoded.length() <= CHUNK_SIZE_BYTES / 4) {
            return List.of(encoded);
        }

        byte[] utf8Bytes = encoded.getBytes(StandardCharsets.UTF_8);

        // If the entire string fits within the limit, return it as a single chunk
        if (utf8Bytes.length <= CHUNK_SIZE_BYTES) {
            return List.of(encoded);
        }

        // Otherwise, chunk by UTF-8 byte boundaries to avoid splitting multibyte characters
        List<String> chunks = new ArrayList<>();
        int offset = 0;

        while (offset < utf8Bytes.length) {
            int chunkEnd = Math.min(offset + CHUNK_SIZE_BYTES, utf8Bytes.length);

            // Ensure we don't split a multibyte UTF-8 character.
            // UTF-8 continuation bytes start with 10xxxxxx (0x80-0xBF).
            // Guard chunkEnd > offset so we always make forward progress on malformed input.
            while (chunkEnd > offset && chunkEnd < utf8Bytes.length && (utf8Bytes[chunkEnd] & 0xC0) == 0x80) {
                chunkEnd--;
            }

            String chunk = new String(utf8Bytes, offset, chunkEnd - offset, StandardCharsets.UTF_8);
            chunks.add(chunk);
            offset = chunkEnd;
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
