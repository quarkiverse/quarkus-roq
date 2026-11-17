package io.quarkiverse.roq.data.runtime.writer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

import org.yaml.snakeyaml.DumperOptions.LineBreak;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.quarkiverse.roq.data.runtime.DataFormat;

/**
 * Merges a data tree into the original content of a data file and writes the result.
 * <p>
 * YAML is updated through its node tree to keep the comments (see {@link YamlDocument}), JSON is written from the
 * merged tree.
 */
public final class DataTreeWriter {

    private static final String LF = LineBreak.UNIX.getString();

    private final ObjectMapper mapper;
    private final TreeMerger merger;

    public DataTreeWriter(ObjectMapper mapper) {
        this.mapper = mapper;
        this.merger = new TreeMerger(new DataSchema(mapper));
    }

    public String write(JsonNode tree, JavaType type, String original, DataFormat format) {
        return switch (format) {
            case YAML -> {
                final YamlDocument document = YamlDocument.parse(mapper, original);
                yield write(tree, type, original, document.data(), document::write);
            }
            case JSON -> write(tree, type, original, parseJson(original), this::writeJson);
        };
    }

    private String write(JsonNode tree, JavaType type, String original, JsonNode originalData,
            Function<JsonNode, String> writer) {
        final JsonNode merged = merger.merge(originalData, tree, type);
        // when the data didn't change, the original content is returned as-is, whatever its layout
        return originalData != null && TreeMerger.same(originalData, merged) ? original : writer.apply(merged);
    }

    private JsonNode parseJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(content);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse the original JSON content", e);
        }
    }

    private String writeJson(JsonNode merged) {
        final DefaultIndenter indenter = new DefaultIndenter("  ", LF);
        final DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                .withSeparators(Separators.createDefaultInstance()
                        .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                        .withObjectEmptySeparator("")
                        .withArrayEmptySeparator(""));
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        try {
            return mapper.writer(printer).writeValueAsString(merged) + LF;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write JSON content", e);
        }
    }
}
