package io.quarkus.tools.migration.jekyll;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Converts Jekyll frontmatter in content files to Roq equivalents.
 * Handles pagination and permalink transformations.
 */
public class JekyllFrontMatterConverter {

    private static final Pattern PAGINATION_BLOCK = Pattern.compile(
            "^pagination:[ \\t]*\\n([ \\t]+.*\\n)*", Pattern.MULTILINE);

    private static final Pattern PERMALINK_LINE = Pattern.compile("^permalink:[ \\t]*(.*)\\n", Pattern.MULTILINE);

    private final YAMLMapper yamlMapper = new YAMLMapper();

    /**
     * Run all frontmatter conversions on content files in a project directory.
     */
    public void convertProject(Path projectDir) throws IOException {
        Path contentDir = projectDir.resolve("content");
        if (!Files.isDirectory(contentDir)) {
            return;
        }

        Path configFile = projectDir.resolve("_config.yml");
        JsonNode config = Files.exists(configFile)
                ? yamlMapper.readTree(Files.readString(configFile))
                : null;

        convertPermalinks(contentDir);
        convertPagination(contentDir, config);
    }

    /**
     * Convert Jekyll permalink frontmatter to Roq aliases.
     * If the permalink matches the filename, it's redundant and removed.
     * Otherwise it becomes an alias.
     */
    public void convertPermalinks(Path contentDir) throws IOException {
        for (Path file : findContentFiles(contentDir)) {
            String content = Files.readString(file);
            Matcher matcher = PERMALINK_LINE.matcher(content);
            if (!matcher.find()) {
                continue;
            }

            String permalinkValue = matcher.group(1).trim()
                    .replaceAll("^['\"]|['\"]$", "");
            // Strip leading and trailing slashes
            String normalized = permalinkValue.replaceAll("^/|/$", "");

            String filenameNoExt = stripExtension(file.getFileName().toString());

            if (normalized.equals(filenameNoExt)) {
                content = PERMALINK_LINE.matcher(content).replaceFirst("");
            } else {
                content = PERMALINK_LINE.matcher(content).replaceFirst("aliases: " + Matcher.quoteReplacement(matcher.group(1)) + "\n");
            }

            Files.writeString(file, content);
        }
    }

    /**
     * Convert Jekyll pagination frontmatter to Roq paginate syntax.
     * Reads collection and per_page from the Jekyll _config.yml pagination block.
     * Derives the link pattern from each page's filename.
     */
    public void convertPagination(Path contentDir, JsonNode config) throws IOException {
        String collection = getPaginationConfig(config, "collection", "posts");
        String size = getPaginationConfig(config, "per_page", "10");

        for (Path file : findContentFiles(contentDir)) {
            String content = Files.readString(file);
            if (!PAGINATION_BLOCK.matcher(content).find()) {
                continue;
            }

            String pageSlug = stripExtension(file.getFileName().toString());
            String replacement = "paginate:\n"
                    + "  collection: " + collection + "\n"
                    + "  size: " + size + "\n"
                    + "  link: " + pageSlug + "/page/:page\n";

            content = PAGINATION_BLOCK.matcher(content).replaceFirst(replacement);
            Files.writeString(file, content);
        }
    }

    private String getPaginationConfig(JsonNode config, String key, String defaultValue) {
        if (config == null || !config.has("pagination")) {
            return defaultValue;
        }
        JsonNode pagination = config.get("pagination");
        if (!pagination.has(key)) {
            return defaultValue;
        }
        return pagination.get(key).asText();
    }

    private Path[] findContentFiles(Path contentDir) throws IOException {
        try (Stream<Path> paths = Files.walk(contentDir)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".md") || name.endsWith(".adoc") || name.endsWith(".asciidoc");
                    })
                    .toArray(Path[]::new);
        }
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }
}
