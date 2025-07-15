package io.quarkiverse.roq.frontmatter.deployment.utils;

import static io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterScanProcessor.ESCAPE_KEY;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqFrontMatterReadingException;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterHeaderParserBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.TemplateContext;
import io.vertx.core.json.JsonObject;

public class AsciidocHeaderParser {
    public record Header(String title, String author, Map<String, Object> attributes) {
    }

    public static RoqFrontMatterHeaderParserBuildItem createBuildItem(boolean escape, Predicate<TemplateContext> isApplicable) {
        return new RoqFrontMatterHeaderParserBuildItem(isApplicable, templateContext -> {
            try {
                Header header = parseHeader(templateContext.content());
                final JsonObject pageData = toPageData(header);
                if (!pageData.containsKey(ESCAPE_KEY)) {
                    pageData.put(ESCAPE_KEY, escape);
                }
                return pageData;
            } catch (IOException e) {
                throw new RoqFrontMatterReadingException(
                        "Error reading AsciiDoc Attributes block in file: %s".formatted(templateContext.templatePath()));
            }
        }, Function.identity(), 20);
    }

    public static Header parseHeader(String content) throws IOException {
        List<String> lines = content.lines().toList();
        String title = null;
        String author = null;
        Map<String, Object> attributes = new LinkedHashMap<>();

        boolean inBlockComment = false;
        boolean afterTitle = false;

        for (String line : lines) {
            String trimmed = line.strip();

            // Block comment start/end
            if (trimmed.equals("////")) {
                inBlockComment = !inBlockComment;
                continue;
            }
            if (inBlockComment) {
                continue;
            }

            // Skip line comments and blank lines
            if (trimmed.isEmpty() || trimmed.startsWith("//")) {
                continue;
            }

            // Title (first = line)
            if (title == null && trimmed.startsWith("=")) {
                title = trimmed.replaceFirst("^=+\\s*", "");
                afterTitle = true;
                continue;
            }

            // Attribute line
            if (trimmed.startsWith(":") && trimmed.contains(":")) {
                // Parse attribute
                int secondColon = trimmed.indexOf(':', 1);
                if (secondColon > 0) {
                    String key = trimmed.substring(1, secondColon).strip();
                    String value = trimmed.substring(secondColon + 1).strip();

                    if (key.startsWith("!")) {
                        key = key.substring(1);
                        value = null;
                    } else if (value.isEmpty()) {
                        value = "true";
                    } else if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    // Validate attribute name simply
                    if (key.matches("[A-Za-z0-9_][A-Za-z0-9_-]*")) {
                        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                            attributes.put(key, Boolean.parseBoolean(value));
                        } else {
                            attributes.put(key, value);
                        }
                        continue;
                    }
                }
            }

            // After title, first non-attribute line is author
            if (afterTitle && author == null) {
                author = trimmed;
                continue;
            }

            // If reached here: non-header content - break parsing header
            break;
        }

        return new Header(title, author, attributes);
    }

    public static JsonObject toPageData(Header header) {
        JsonObject pageData = new JsonObject();
        for (String key : header.attributes.keySet()) {
            if (key.startsWith("page-")) {
                pageData.put(key.substring(5), header.attributes.get(key));
            }
        }
        if (header.title != null) {
            pageData.put("title", header.title);
        }
        if (header.author != null) {
            pageData.put("author", header.author);
        }
        return pageData;
    }

}
