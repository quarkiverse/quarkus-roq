package io.quarkiverse.roq.data.runtime.writer;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.roq.data.runtime.DataFormat;

/**
 * Merges a data tree into the original content of a data file and writes the result.
 */
public final class DataTreeWriter {

    private final ObjectMapper mapper;
    private final TreeMerger merger;

    public DataTreeWriter(ObjectMapper mapper) {
        this.mapper = mapper;
        this.merger = new TreeMerger(new DataSchema(mapper));
    }

    public String write(JsonNode tree, JavaType type, String original, DataFormat format) {
        final DataDocument document = switch (format) {
            case YAML -> YamlDocument.parse(mapper, original);
            case JSON -> JsonDocument.parse(mapper, original);
        };
        final JsonNode merged = merger.merge(document.data(), tree, type);
        return unchanged(document.data(), merged) ? original : document.write(merged);
    }

    /**
     * When the data didn't change, the original content is returned as-is, whatever its layout.
     */
    private static boolean unchanged(JsonNode original, JsonNode merged) {
        return original != null && TreeMerger.same(original, merged);
    }
}
