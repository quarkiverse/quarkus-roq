package io.quarkiverse.roq.data.runtime.writer;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.yaml.snakeyaml.DumperOptions.LineBreak;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A JSON document which can be updated from a data tree and printed back, keeping the original key order, indentation,
 * line breaks and trailing new line.
 */
final class JsonDocument implements DataDocument {

    private final ObjectMapper mapper;
    private final JsonNode data;
    private final Style style;

    private JsonDocument(ObjectMapper mapper, JsonNode data, Style style) {
        this.mapper = mapper;
        this.data = data;
        this.style = style;
    }

    static JsonDocument parse(ObjectMapper mapper, String content) {
        if (content == null || content.isBlank()) {
            return new JsonDocument(mapper, null, Style.DEFAULT);
        }
        try {
            return new JsonDocument(mapper, mapper.readTree(content), Style.detect(content));
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to parse the original JSON content", e);
        }
    }

    @Override
    public JsonNode data() {
        return data;
    }

    @Override
    public String write(JsonNode merged) {
        try {
            String result;
            if (style.indent == null) {
                result = mapper.writeValueAsString(merged);
            } else {
                final DefaultIndenter indenter = new DefaultIndenter(style.indent, LineBreak.UNIX.getString());
                final DefaultPrettyPrinter printer = new DefaultPrettyPrinter()
                        .withSeparators(Separators.createDefaultInstance()
                                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                                .withObjectEmptySeparator("")
                                .withArrayEmptySeparator(""));
                printer.indentObjectsWith(indenter);
                printer.indentArraysWith(indenter);
                // JSON strings never contain raw new lines, so this only affects the layout
                result = mapper.writer(printer).writeValueAsString(merged)
                        .replace(LineBreak.UNIX.getString(), style.lineBreak.getString());
            }
            return style.trailingLineBreak ? result + style.lineBreak.getString() : result;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write JSON content", e);
        }
    }

    /**
     * @param indent the indentation unit, {@code null} for compact output
     */
    record Style(String indent, LineBreak lineBreak, boolean trailingLineBreak) {

        static final Style DEFAULT = new Style("  ", LineBreak.UNIX, true);

        static Style detect(String content) {
            final LineBreak lineBreak = content.contains(LineBreak.WIN.getString()) ? LineBreak.WIN : LineBreak.UNIX;
            final boolean trailingLineBreak = content.endsWith(LineBreak.UNIX.getString());
            final String body = content.strip();
            if (!body.contains(LineBreak.UNIX.getString())) {
                return new Style(null, lineBreak, trailingLineBreak);
            }
            for (String line : body.lines().toList()) {
                final String stripped = line.stripLeading();
                if (!stripped.isEmpty() && stripped.length() < line.length()) {
                    return new Style(line.substring(0, line.length() - stripped.length()), lineBreak,
                            trailingLineBreak);
                }
            }
            return new Style(DEFAULT.indent, lineBreak, trailingLineBreak);
        }
    }
}
