package io.quarkus.tools;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

/**
 * Converts Jekyll _config.yml to Roq application.properties and data/siteConfig.yml.
 * Replaces the bash script logic from roq-it-jekyll lines 61-62, 184-192, and 238-277.
 */
public class JekyllConfigConverter {

    public static final String DEFAULT_COLLECTION_NAME = "posts";
    public static final String APPLICATION_PROPERTIES = "application.properties";
    public static final String CONFIG_DIR = "config";
    public static final String DATA_DIR = "data";
    public static final String SITE_CONFIG_FILE = "siteConfig.yml";
    private final YAMLMapper yamlMapper;
    private final ObjectMapper objectMapper;
    private boolean strictProperties;

    public JekyllConfigConverter() {
        this.yamlMapper = new YAMLMapper();
        this.objectMapper = new ObjectMapper();
    }

    public void setStrictProperties(boolean strictProperties) {
        this.strictProperties = strictProperties;
    }

    /**
     * Create application.properties values with standard Roq properties for Jekyll compatibility.
     *
     * @return Application properties (without plugin-dependent properties)
     */
    public Properties createApplicationProperties() {
        return createApplicationProperties(null);
    }

    /**
     * Create application.properties values with standard Roq properties for Jekyll compatibility,
     * including properties derived from detected Jekyll plugins.
     *
     * @param config Parsed _config.yml (can be null)
     * @return Application properties
     */
    public Properties createApplicationProperties(JsonNode config) {
        Properties properties = new Properties();

        // Always enable the alternative expression syntax to reduce overhead of escaping braces
        properties.setProperty("quarkus.qute.alt-expr-syntax", "true");
        // Set a date format with a sensible default for Jekyll.
        properties.setProperty("site.date-format", "yyyy-MM-dd['T'HH:mm:ss][X]");
        // Jekyll templates access arbitrary frontmatter fields that may not exist on every page
        // echo "quarkus.qute.strict-rendering=false" >> ${project_dir}/config/application.properties
        properties.setProperty("quarkus.qute.strict-rendering", "false");
        if (!strictProperties) {
            // Liquid silently swallows missing properties (outputs nothing).
            // NOOP replicates that behaviour so converted templates work without
            // wrapping every optional field in {#if}.
            properties.setProperty("quarkus.qute.property-not-found-strategy", "NOOP");
        }
        // Exclude type checking for:
        // - Object.* (JsonArray iteration yields Object at build time)
        // - Page.paginator (only on NormalPage subclass, not visible at compile time)
        // - DocumentPage.* (post loop variables access custom frontmatter via data)
        properties.setProperty("quarkus.qute.type-check-excludes",
                "java.lang.Object.*,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.paginator,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.tags,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.tagsCount,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage.*");

        // Jekyll SCSS often uses absolute url('/assets/...') references. EsBuild can't resolve
        // these at build time since it doesn't know public/ is the site root. Mark them as external.
        properties.setProperty("quarkus.web-bundler.bundling.external", "/assets/*");

        if (hasPlugin(config, "jekyll-auto-authors")) {
            addAutoAuthorProperties(config, properties);
        }

        if (config != null && config.has("url")) {
            properties.setProperty("site.url", config.get("url").asText());
        }

        // Roq sets showtitle + noheader by default, which renders the AsciiDoc title
        // in the body. Jekyll layouts render page.title as <h1>, so unset showtitle
        // to avoid a duplicate title.
        properties.setProperty("quarkus.asciidoc.attributes.\"!showtitle\"", "true");

        addAsciidoctorAttributes(config, properties);
        addCollectionProperties(config, properties);
        addEscapedPages(config, properties);

        return properties;
    }

    /**
     * Create a siteConfig.yml file for template access to site properties.
     * This makes Jekyll's site.* properties available as cdi:siteConfig.* in Roq.
     * Replaces roq-it-jekyll lines 238-277.
     *
     * @param configYaml The content of _config.yml
     * @param cnameContent Optional CNAME file content (can be null)
     * @return YAML content for data/siteConfig.yml
     * @throws IOException if parsing fails
     */
    public String createSiteConfigYaml(String configYaml, String cnameContent) throws IOException {
        return createSiteConfigYaml(yamlMapper.readTree(configYaml), cnameContent);
    }

    public static final String COLLECTIONS = "collections";
    /**
     * Create a siteConfig.yml from a pre-parsed config.
     */
    private static final Set<String> KEYS_HANDLED_ELSEWHERE = Set.of(
            "url", // → application.properties site.url
            COLLECTIONS, // → application.properties site.collections.*
            "plugins", // → application.properties (auto-author, etc.)
            "defaults", // → application.properties (collection layouts)
            "description", // → index page frontmatter
            "autopages", // → application.properties (auto-author config)
            "title", // → index page frontmatter / Roq site.title
            "asciidoctor" // → application.properties quarkus.asciidoc.attributes.*
    );

    public String createSiteConfigYaml(JsonNode config, String cnameContent) throws IOException {
        Map<String, Object> siteConfig = new LinkedHashMap<>();

        // Add CNAME
        siteConfig.put("cname", cnameContent != null ? cnameContent.trim() : "");

        // Copy all config keys except those handled by application.properties or index page
        // Transform all hyphenated keys to camelCase for Qute template compatibility
        // (Qute doesn't support hyphens in property access - interprets as subtraction)
        config.fieldNames().forEachRemaining(key -> {
            if (KEYS_HANDLED_ELSEWHERE.contains(key)) {
                return;
            }
            copyNodeWithCamelCaseKeys(config.get(key), siteConfig, key);
        });

        // Add empty tags array (for Jekyll compatibility)
        siteConfig.put("tags", new Object[0]);

        // Convert to YAML
        return yamlMapper.writeValueAsString(siteConfig);

    }

    private void copyNodeWithCamelCaseKeys(JsonNode node, Map<String, Object> target, String key) {
        if (node.isObject()) {
            // Recursively transform all nested object keys to camelCase
            Map<String, Object> nestedMap = new LinkedHashMap<>();
            node.fields().forEachRemaining(entry -> {
                String camelKey = hyphenToCamelCase(entry.getKey());
                copyNodeWithCamelCaseKeys(entry.getValue(), nestedMap, camelKey);
            });
            target.put(key, nestedMap);
        } else if (node.isArray()) {
            target.put(key, objectMapper.convertValue(node, Object.class));
        } else if (node.isTextual()) {
            target.put(key, node.asText());
        } else if (node.isNumber()) {
            target.put(key, node.numberValue());
        } else if (node.isBoolean()) {
            target.put(key, node.asBoolean());
        } else if (node.isNull()) {
            target.put(key, null);
        }
    }

    /**
     * Convert Jekyll config files from a project directory.
     * Reads _config.yml and CNAME, creates config/application.properties and data/siteConfig.yml.
     * Replaces roq-it-jekyll lines 61-62, 184-192, and 238-277.
     *
     * <p>
     * <strong>Warning:</strong> This method performs destructive file operations:
     * </p>
     * <ul>
     * <li>Moves collection directories from {@code _<name>} to {@code content/<name>}
     * (e.g., {@code _guides} → {@code content/guides})</li>
     * <li>Modifies {@code index.md/index.html/index.adoc} frontmatter to add site description</li>
     * <li>Creates/overwrites {@code config/application.properties} and {@code data/siteConfig.yml}</li>
     * </ul>
     *
     * <p>
     * Run on a clean working tree or ensure you have backups before conversion.
     * </p>
     *
     * @param projectDir The Jekyll project directory
     * @throws IOException if file operations fail, including if collection directory moves fail
     */
    public void convertProject(Path projectDir) throws IOException {
        Path configFile = projectDir.resolve("_config.yml");
        Path cnameFile = projectDir.resolve("CNAME");

        if (!Files.exists(configFile)) {
            throw new IOException("_config.yml not found in " + projectDir + ". Is this a Jekyll project?");
        }

        // Read input files
        String configYaml = Files.readString(configFile);
        String cnameContent = Files.exists(cnameFile) ? Files.readString(cnameFile) : null;

        // Create config/application.properties
        Path configDir = projectDir.resolve(CONFIG_DIR);
        Files.createDirectories(configDir);
        Path propsFile = configDir.resolve(APPLICATION_PROPERTIES);

        JsonNode config = yamlMapper.readTree(configYaml);

        // Write properties manually — Properties.store() escapes colons in values,
        // which corrupts date format patterns like yyyy-MM-dd['T'HH:mm:ss][X]
        try (Writer writer = Files.newBufferedWriter(propsFile)) {
            Properties props = createApplicationProperties(config);
            for (String key : props.stringPropertyNames().stream().sorted().toList()) {
                writer.write(key + "=" + props.getProperty(key) + "\n");
            }
        }

        // Create data/siteConfig.yml
        String siteConfigYaml = createSiteConfigYaml(config, cnameContent);
        Path dataDir = projectDir.resolve(DATA_DIR);
        Files.createDirectories(dataDir);
        Path siteConfigFile = dataDir.resolve(SITE_CONFIG_FILE);
        Files.writeString(siteConfigFile, siteConfigYaml);

        // Move Jekyll collection directories (_<name>) to Roq content/<name>
        moveCollectionDirectories(projectDir, config);

        // Add site description to index page frontmatter (Roq reads site.description from there)
        if (config.has("description")) {
            addDescriptionToIndexPage(projectDir, config.get("description").asText());
        }
    }

    void moveCollectionDirectories(Path projectDir, JsonNode config) throws IOException {
        if (!config.has(COLLECTIONS)) {
            return;
        }
        JsonNode collections = config.get(COLLECTIONS);
        if (!collections.isObject()) {
            return;
        }
        Path contentDir = projectDir.resolve("content");
        List<String> failures = new ArrayList<>();
        collections.fieldNames().forEachRemaining(name -> {
            if (DEFAULT_COLLECTION_NAME.equals(name)) {
                return;
            }
            Path source = projectDir.resolve("_" + name);
            if (Files.isDirectory(source)) {
                Path target = contentDir.resolve(name);
                try {
                    Files.createDirectories(target.getParent());
                    Files.move(source, target);
                } catch (IOException e) {
                    failures.add("Failed to move _" + name + " to content/" + name + ": " + e.getMessage());
                }
            }
        });
        if (!failures.isEmpty()) {
            throw new IOException("Collection directory migration failed:\n  " + String.join("\n  ", failures));
        }
    }

    void addDescriptionToIndexPage(Path projectDir, String description) throws IOException {
        Path indexFile = findIndexFile(projectDir);
        if (indexFile == null) {
            return;
        }
        String content = Files.readString(indexFile);
        if (content.contains("description:")) {
            return;
        }
        // Insert description after the opening --- (handle both LF and CRLF line endings)
        String escapedDescription = description.replace("\\", "\\\\").replace("\"", "\\\"");
        content = content.replaceFirst("(---\\s*(?:\\r\\n|\\n))", "$1description: \"" +
                escapedDescription + "\"\n");
        Files.writeString(indexFile, content);
    }

    private Path findIndexFile(Path projectDir) {
        for (String dir : new String[] { "", "content" }) {
            for (String name : new String[] { "index.md", "index.html", "index.adoc" }) {
                Path p = projectDir.resolve(dir).resolve(name);
                if (Files.exists(p)) {
                    return p;
                }
            }
        }
        return null;
    }

    private boolean hasPlugin(JsonNode config, String pluginName) {
        if (config == null || !config.has("plugins")) {
            return false;
        }
        JsonNode plugins = config.get("plugins");
        if (!plugins.isArray()) {
            return false;
        }
        for (JsonNode plugin : plugins) {
            if (pluginName.equals(plugin.asText())) {
                return true;
            }
        }
        return false;
    }

    private void addAsciidoctorAttributes(JsonNode config, Properties properties) {
        if (config == null || !config.has("asciidoctor")) {
            return;
        }
        JsonNode asciidoctor = config.get("asciidoctor");
        if (!asciidoctor.has("attributes")) {
            return;
        }
        JsonNode attributes = asciidoctor.get("attributes");
        if (!attributes.isObject()) {
            return;
        }
        attributes.fields().forEachRemaining(entry -> {
            String value = entry.getValue().asText();
            if (!value.isEmpty()) {
                properties.setProperty("quarkus.asciidoc.attributes." + entry.getKey(), value);
            }
        });
    }

    private void addCollectionProperties(JsonNode config, Properties properties) {
        Map<String, String> permalinks = getCollectionPermalinks(config);

        if (config != null && config.has(COLLECTIONS)) {
            JsonNode collections = config.get(COLLECTIONS);
            if (collections.isObject()) {
                collections.fields().forEachRemaining(entry -> {
                    String name = entry.getKey();
                    if (DEFAULT_COLLECTION_NAME.equals(name)) {
                        return;
                    }
                    JsonNode collectionConfig = entry.getValue();
                    boolean output = collectionConfig.isObject()
                            && collectionConfig.has("output")
                            && collectionConfig.get("output").asBoolean(false);
                    if (!output) {
                        return;
                    }
                    properties.setProperty("site.collections." + name, "true");
                    if (collectionConfig.has("layout")) {
                        properties.setProperty("site.collections." + name + ".layout",
                                collectionConfig.get("layout").asText());
                    } else {
                        String defaultLayout = getDefaultLayout(config, name);
                        if (defaultLayout != null) {
                            properties.setProperty("site.collections." + name + ".layout", defaultLayout);
                        }
                    }
                });
            }
        }

        permalinks.forEach((name, linkTemplate) -> properties.setProperty("site.collections." + name + ".link", linkTemplate));
    }

    private Map<String, String> getCollectionPermalinks(JsonNode config) {
        Map<String, String> result = new LinkedHashMap<>();
        if (config == null || !config.has("defaults")) {
            return result;
        }
        JsonNode defaults = config.get("defaults");
        if (!defaults.isArray()) {
            return result;
        }
        for (JsonNode entry : defaults) {
            if (!entry.has("scope") || !entry.has("values")) {
                continue;
            }
            JsonNode scope = entry.get("scope");
            if (!scope.has("type")) {
                continue;
            }
            String type = scope.get("type").asText();
            JsonNode values = entry.get("values");
            if (values.has("permalink")) {
                String permalink = values.get("permalink").asText();
                result.put(type, translatePermalinkPlaceholders(permalink));
            }
        }
        return result;
    }

    static String translatePermalinkPlaceholders(String permalink) {
        return permalink
                .replace(":path", ":dir[1:]/:name")
                .replace(":title", ":name")
                .replace(":categories", ":collection");
    }

    private static final Set<String> SKIP_ESCAPE_COLLECTIONS = Set.of(DEFAULT_COLLECTION_NAME, "redirects");

    private void addEscapedPages(JsonNode config, Properties properties) {
        if (config == null || !config.has(COLLECTIONS)) {
            return;
        }
        JsonNode collections = config.get(COLLECTIONS);
        if (!collections.isObject()) {
            return;
        }
        StringBuilder escaped = new StringBuilder();
        collections.fieldNames().forEachRemaining(name -> {
            if (SKIP_ESCAPE_COLLECTIONS.contains(name)) {
                return;
            }
            if (!escaped.isEmpty()) {
                escaped.append(",");
            }
            escaped.append(name).append("/**");
        });
        if (!escaped.isEmpty()) {
            properties.setProperty("site.escaped-pages", escaped.toString());
        }
    }

    private void addAutoAuthorProperties(JsonNode config, Properties properties) {
        String layout = "author";
        String dataName = "authors";

        if (config != null && config.has("autopages")) {
            JsonNode autopages = config.get("autopages");
            if (autopages.has("authors")) {
                JsonNode authors = autopages.get("authors");
                if (authors.has("layouts") && authors.get("layouts").isArray()
                        && authors.get("layouts").size() > 0) {
                    layout = stripExtension(authors.get("layouts").get(0).asText());
                }
                if (authors.has("data")) {
                    dataName = stripExtension(stripPath(authors.get("data").asText()));
                }
            }
        }

        properties.setProperty("site.collections.author.layout", layout);
        properties.setProperty("site.collections.author.from-data.id-key", "_key");
        properties.setProperty("site.collections.author.from-data.name", dataName);
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private String stripPath(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    private String getDefaultLayout(JsonNode config, String collectionName) {
        if (!config.has("defaults")) {
            return null;
        }
        JsonNode defaults = config.get("defaults");
        if (!defaults.isArray()) {
            return null;
        }
        for (JsonNode entry : defaults) {
            if (!entry.has("scope") || !entry.has("values")) {
                continue;
            }
            JsonNode scope = entry.get("scope");
            if (scope.has("type") && collectionName.equals(scope.get("type").asText())) {
                JsonNode values = entry.get("values");
                if (values.has("layout")) {
                    return values.get("layout").asText();
                }
            }
        }
        return null;
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String key) {
        copyIfPresent(source, target, key, key);
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String sourceKey, String targetKey) {
        if (source.has(sourceKey)) {
            JsonNode value = source.get(sourceKey);
            if (value.isTextual()) {
                target.put(targetKey, value.asText());
            } else if (value.isNumber()) {
                target.put(targetKey, value.numberValue());
            } else if (value.isBoolean()) {
                target.put(targetKey, value.asBoolean());
            } else if (value.isObject() || value.isArray()) {
                try {
                    target.put(targetKey, objectMapper.convertValue(value, Object.class));
                } catch (IllegalArgumentException e) {
                    System.err
                            .println("Warning: could not convert config value for key '" + sourceKey + "': " + e.getMessage());
                }
            }
        }
    }

    static String hyphenToCamelCase(String key) {
        if (!key.contains("-")) {
            return key;
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : key.toCharArray()) {
            if (c == '-') {
                capitalizeNext = true;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
