package io.quarkus.tools;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import io.quarkus.qute.Engine;
import io.quarkus.qute.Template;

/**
 * Converts Jekyll _config.yml to Roq application.properties and data/siteConfig.yml.
 * Replaces the bash script logic from roq-it-jekyll lines 61-62, 184-192, and 238-277.
 */
public class JekyllConfigConverter {

    public static final String COLLECTIONS = "collections";
    public static final String DEFAULT_COLLECTION_NAME = "posts";
    public static final String APPLICATION_PROPERTIES = "application.properties";
    public static final String CONFIG_DIR = "config";
    public static final String DATA_DIR = "data";
    public static final String SITE_CONFIG_FILE = "siteConfig.yml";
    private final YAMLMapper yamlMapper;
    private final ObjectMapper objectMapper;
    private boolean strictProperties;
    private final Engine quteEngine;

    public JekyllConfigConverter() {
        this.yamlMapper = new YAMLMapper();
        this.objectMapper = new ObjectMapper();
        this.quteEngine = Engine.builder()
                .addDefaults()
                .build();
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
        addIgnoredFiles(config, properties);

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

    /**
     * Create a siteConfig.yml from a pre-parsed config.
     */
    private static final Set<String> KEYS_HANDLED_ELSEWHERE = Set.of(
            "url", // → application.properties site.url
            "collections", // → application.properties site.collections.*
            "plugins", // → application.properties (auto-author, etc.)
            "defaults", // → application.properties (collection layouts)
            "description", // → index page frontmatter
            "autopages", // → application.properties (auto-author config)
            "title", // → index page frontmatter / Roq site.title
            "asciidoctor", // → application.properties quarkus.asciidoc.attributes.*
            "exclude", // → application.properties site.ignored-files
            "search" // → ConfigMapping + application.properties {project}.search.*
    );

    public String createSiteConfigYaml(JsonNode config, String cnameContent) throws IOException {
        Map<String, Object> siteConfig = new LinkedHashMap<>();

        // Add CNAME
        siteConfig.put("cname", cnameContent != null ? cnameContent.trim() : "");

        // Copy all config keys except those handled by application.properties or ConfigMappings
        config.fieldNames().forEachRemaining(key -> {
            if (KEYS_HANDLED_ELSEWHERE.contains(key)) {
                return;
            }
            copyIfPresent(config, siteConfig, key);
        });

        // Convert to YAML
        return yamlMapper.writeValueAsString(siteConfig);
    }

    /**
     * Convert Jekyll config files from a project directory.
     * Reads _config.yml and CNAME, creates config/application.properties and data/siteConfig.yml.
     * Replaces roq-it-jekyll lines 61-62, 184-192, and 238-277.
     *
     * @param projectDir The Jekyll project directory
     * @throws IOException if file operations fail
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

        // Derive project name early — needed for ConfigMapping prefix
        String projectName = deriveProjectName(projectDir, config);
        // Use project-derived prefix for ConfigMapping properties to avoid
        // clashing with the Roq framework's site.* ConfigMapping namespace
        String configMappingPrefix = configMappingPrefix(projectName);

        // Track config mappings from main config too
        Map<String, JsonNode> configMappingsToGenerate = new LinkedHashMap<>();

        // Write properties manually — Properties.store() escapes colons in values,
        // which corrupts date format patterns like yyyy-MM-dd['T'HH:mm:ss][X]
        try (Writer writer = Files.newBufferedWriter(propsFile)) {
            Properties props = createApplicationProperties(config);
            for (String key : props.stringPropertyNames().stream().sorted().toList()) {
                writer.write(key + "=" + props.getProperty(key) + "\n");
            }

            // Handle search config from main _config.yml
            if (config.has("search") && config.get("search").isObject()) {
                JsonNode search = config.get("search");
                List<String> searchProps = buildSiteConfigOverrides(
                        objectMapper.createObjectNode().set("search", search), configMappingPrefix);
                for (String line : searchProps) {
                    writer.write(line + "\n");
                }
                configMappingsToGenerate.put("search", search);
            }

            convertOverlayConfigs(projectDir, writer, configMappingsToGenerate, configMappingPrefix);
        }

        // Generate ConfigMapping interfaces for all config sections
        if (!configMappingsToGenerate.isEmpty()) {
            generateConfigMappings(projectDir, projectName, configMappingPrefix, configMappingsToGenerate);
            addJandexPluginToPom(projectDir);

            Path metadataFile = projectDir.resolve("config/.migration-config-mappings");
            Files.writeString(metadataFile, String.join("\n", configMappingsToGenerate.keySet()) + "\n");
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

        // Add name and simple-name to index page frontmatter (Roq templates use site.data.name and site.data.simple-name)
        if (config.has("title")) {
            addNameToIndexPage(projectDir, config.get("title").asText());
        }

        // Add tagging frontmatter to the tag layout (must run before LiquidToQuteCommand)
        addTaggingFrontmatter(projectDir, config);
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
                    System.err.println("Warning: could not move _" + name + " to content/" + name + ": " + e.getMessage());
                }
            }
        });
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
        // Insert description after the opening ---
        content = content.replaceFirst("(---\\s*\\n)", "$1description: \"" +
                description.replace("\"", "\\\"") + "\"\n");
        Files.writeString(indexFile, content);
    }

    void addNameToIndexPage(Path projectDir, String title) throws IOException {
        Path indexFile = findIndexFile(projectDir);
        if (indexFile == null) {
            return;
        }
        String content = Files.readString(indexFile);
        StringBuilder toInsert = new StringBuilder();
        if (!content.contains("name:")) {
            toInsert.append("name: \"").append(title.replace("\"", "\\\"")).append("\"\n");
        }
        if (!content.contains("simple-name:")) {
            toInsert.append("simple-name: \"").append(title.replace("\"", "\\\"")).append("\"\n");
        }
        if (!toInsert.isEmpty()) {
            content = content.replaceFirst("(---\\s*\\n)", "$1" + toInsert);
            Files.writeString(indexFile, content);
        }
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

    void addTaggingFrontmatter(Path projectDir, JsonNode config) throws IOException {
        if (config == null || !config.has("jekyll-archives")) {
            return;
        }
        JsonNode archives = config.get("jekyll-archives");
        if (!archives.has("layouts")) {
            return;
        }
        JsonNode layouts = archives.get("layouts");
        if (!layouts.has("tag")) {
            return;
        }
        String layoutName = layouts.get("tag").asText();

        String link = null;
        if (archives.has("permalinks")) {
            JsonNode permalinks = archives.get("permalinks");
            if (permalinks.has("tag")) {
                link = permalinks.get("tag").asText().replace(":name", ":tag");
            }
        }

        Path layoutFile = projectDir.resolve("_layouts/" + layoutName + ".html");
        if (!Files.exists(layoutFile)) {
            return;
        }

        String content = Files.readString(layoutFile);
        if (content.contains("tagging:")) {
            return;
        }

        String taggingBlock;
        if (link != null) {
            taggingBlock = "tagging:\n  collection: posts\n  link: " + link + "\n";
        } else {
            taggingBlock = "tagging: posts\n";
        }

        content = content.replaceFirst("(---\\s*\\n)", "$1" + taggingBlock.replace("\\", "\\\\"));
        Files.writeString(layoutFile, content);
        System.out.println("  [TAGGING] Added tagging frontmatter to " + layoutName + ".html");
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
                .replace(":path", ":dir[1]/:name")
                .replace(":title", ":name")
                .replace(":categories", ":collection");
    }

    private static final Set<String> SKIP_ESCAPE_COLLECTIONS = Set.of("redirects");

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

    private void addIgnoredFiles(JsonNode config, Properties properties) {
        String ignored = buildIgnoredFiles(config);
        if (ignored != null) {
            properties.setProperty("site.ignored-files", ignored);
        }
    }

    String buildIgnoredFiles(JsonNode config) {
        if (config == null || !config.has("exclude")) {
            return null;
        }
        JsonNode exclude = config.get("exclude");
        if (!exclude.isArray()) {
            return null;
        }
        StringBuilder ignored = new StringBuilder();
        for (JsonNode entry : exclude) {
            String pattern = entry.asText();
            if (pattern.startsWith("_")) {
                pattern = pattern.substring(1);
            }
            if (!pattern.endsWith("/**") && !pattern.endsWith("/")) {
                pattern = pattern + "/**";
            }
            if (!ignored.isEmpty()) {
                ignored.append(",");
            }
            ignored.append(pattern);
        }
        return ignored.isEmpty() ? null : ignored.toString();
    }

    void convertOverlayConfigs(Path projectDir, Writer mainPropsWriter,
            Map<String, JsonNode> configMappingsToGenerate, String configMappingPrefix) throws IOException {
        List<Path> overlays;
        try (Stream<Path> files = Files.list(projectDir)) {
            overlays = files
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".yml")
                                && name.startsWith("_")
                                && name.contains("config")
                                && !name.equals("_config.yml");
                    })
                    .sorted()
                    .toList();
        }
        Path configDir = projectDir.resolve("config");

        for (Path overlay : overlays) {
            JsonNode overlayConfig = yamlMapper.readTree(Files.readString(overlay));
            String profile = deriveProfileName(overlay.getFileName().toString());

            // Build properties for all config values
            List<String> propertyLines = buildSiteConfigOverrides(overlayConfig, configMappingPrefix);

            // Also handle ignored-files from exclude
            String ignored = buildIgnoredFiles(overlayConfig);
            if (ignored != null) {
                propertyLines.add("site.ignored-files=" + ignored);
            }

            if (propertyLines.isEmpty()) {
                Files.delete(overlay);
                continue;
            }

            // Write properties with profile prefix if dev, otherwise to separate file
            if ("dev".equals(profile)) {
                for (String line : propertyLines) {
                    mainPropsWriter.write("%" + profile + "." + line + "\n");
                }
            } else {
                Path profileProps = configDir.resolve("application-" + profile + ".properties");
                try (Writer writer = Files.newBufferedWriter(profileProps)) {
                    for (String line : propertyLines) {
                        writer.write(line + "\n");
                    }
                }
            }

            // Track config sections that need ConfigMappings (merge with main config sections)
            overlayConfig.fieldNames().forEachRemaining(key -> {
                if (!"exclude".equals(key) && overlayConfig.get(key).isObject()) {
                    // Merge fields from both main and profile configs
                    JsonNode existing = configMappingsToGenerate.get(key);
                    if (existing != null && existing.isObject()) {
                        // Merge the two objects - profile config adds to base config
                        ((com.fasterxml.jackson.databind.node.ObjectNode) existing)
                                .setAll((com.fasterxml.jackson.databind.node.ObjectNode) overlayConfig.get(key));
                    } else {
                        configMappingsToGenerate.put(key, overlayConfig.get(key));
                    }
                }
            });

            Files.delete(overlay);
            System.out.println("  [CONFIG] Converted and deleted: " + overlay.getFileName());
        }
    }

    List<String> buildSiteConfigOverrides(JsonNode config) {
        return buildSiteConfigOverrides(config, "site");
    }

    List<String> buildSiteConfigOverrides(JsonNode config, String configMappingPrefix) {
        List<String> lines = new java.util.ArrayList<>();
        if (config == null) {
            return lines;
        }
        config.fieldNames().forEachRemaining(key -> {
            if ("exclude".equals(key)) {
                return;
            }
            JsonNode value = config.get(key);
            if (value.isObject()) {
                // Object sections use the configMappingPrefix to avoid clashing
                // with the Roq framework's site.* ConfigMapping
                value.fields().forEachRemaining(field -> {
                    if (field.getValue().isValueNode()) {
                        // Keep kebab-case keys — SmallRye ConfigMapping expects them
                        lines.add(configMappingPrefix + "." + key + "." + field.getKey()
                                + "=" + field.getValue().asText());
                    }
                });
            } else if (value.isValueNode()) {
                lines.add("site." + key + "=" + value.asText());
            }
        });
        return lines;
    }

    static String deriveProfileName(String filename) {
        String name = filename;
        if (name.startsWith("_")) {
            name = name.substring(1);
        }
        if (name.endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }
        name = name.replace("_config", "").replace("config_", "").replace("config", "");
        if (name.startsWith("_")) {
            name = name.substring(1);
        }
        if (name.endsWith("_")) {
            name = name.substring(0, name.length() - 1);
        }
        name = name.replace('_', '-');
        return name.isEmpty() ? "overlay" : name;
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

    /**
     * Generate ConfigMapping interfaces and CDI wrapper beans for config sections.
     */
    void generateConfigMappings(Path projectDir, String projectName, String configMappingPrefix,
            Map<String, JsonNode> configSections)
            throws IOException {

        // Load Qute templates
        Template configMappingTemplate = loadTemplate("ConfigMapping.java");
        Template configBeanTemplate = loadTemplate("ConfigBean.java");

        for (Map.Entry<String, JsonNode> entry : configSections.entrySet()) {
            String sectionName = entry.getKey();
            JsonNode sectionConfig = entry.getValue();

            String packageName = projectName + "." + sectionName + ".config";
            String packagePath = packageName.replace('.', '/');
            Path javaDir = projectDir.resolve("src/main/java/" + packagePath);
            Files.createDirectories(javaDir);

            String interfaceName = capitalize(hyphenToCamelCase(sectionName)) + "Config";
            String producerClassName = capitalize(hyphenToCamelCase(sectionName)) + "ConfigProducer";
            String beanName = hyphenToCamelCase(sectionName) + "Config";

            // Prepare properties for the template
            List<Map<String, String>> properties = new java.util.ArrayList<>();
            sectionConfig.fields().forEachRemaining(field -> {
                String propertyName = hyphenToCamelCase(field.getKey());
                String javaType = inferJavaType(field.getValue());
                properties.add(Map.of(
                        "javaType", javaType,
                        "methodName", propertyName));
            });

            // Generate ConfigMapping interface with @TemplateData
            String interfaceCode = configMappingTemplate
                    .data("packageName", packageName)
                    .data("configMappingPrefix", configMappingPrefix)
                    .data("sectionName", sectionName)
                    .data("interfaceName", interfaceName)
                    .data("properties", properties)
                    .render();

            Files.writeString(javaDir.resolve(interfaceName + ".java"), interfaceCode);

            // Generate CDI producer for template access
            String producerCode = configBeanTemplate
                    .data("packageName", packageName)
                    .data("beanName", beanName)
                    .data("className", producerClassName)
                    .data("interfaceName", interfaceName)
                    .render();

            Files.writeString(javaDir.resolve(producerClassName + ".java"), producerCode);

            System.out.println("  [CONFIG] Generated ConfigMapping: " + packageName + "." + interfaceName);
            System.out.println("  [CONFIG] Generated CDI producer: " + packageName + "." + producerClassName);
            System.out.println("  [CONFIG] Template access: {cdi:" + beanName + ".propertyName}");
        }

        // Generate beans.xml to enable CDI bean discovery for ConfigMapping classes.
        // Only needed when we actually generated ConfigMapping classes.
        // Quarkus normally uses Jandex to discover beans, but it doesn't automatically
        // index application classes. beans.xml with bean-discovery-mode="all" tells
        // CDI to discover all classes regardless of indexing.
        if (!configSections.isEmpty()) {
            Path metaInfDir = projectDir.resolve("src/main/resources/META-INF");
            Files.createDirectories(metaInfDir);
            Path beansXml = metaInfDir.resolve("beans.xml");
            if (!Files.exists(beansXml)) {
                String beansXmlContent = """
                        <?xml version="1.0" encoding="UTF-8"?>
                        <beans xmlns="https://jakarta.ee/xml/ns/jakartaee"
                               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                               xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_4_0.xsd"
                               version="4.0"
                               bean-discovery-mode="all">
                        </beans>
                        """;
                Files.writeString(beansXml, beansXmlContent);
                System.out.println("  [CONFIG] Generated META-INF/beans.xml for CDI discovery");
            }
        }
    }

    private Template loadTemplate(String templateName) throws IOException {
        String templateContent = new String(
                getClass().getResourceAsStream("/templates/" + templateName).readAllBytes());
        return quteEngine.parse(templateContent);
    }

    private String inferJavaType(JsonNode value) {
        if (value.isInt()) {
            return "int";
        } else if (value.isBoolean()) {
            return "boolean";
        } else if (value.isDouble() || value.isFloat()) {
            return "double";
        } else {
            return "String";
        }
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    void addJandexPluginToPom(Path projectDir) throws IOException {
        Path pomFile = projectDir.resolve("pom.xml");
        if (!Files.exists(pomFile)) {
            System.err.println("Warning: pom.xml not found, cannot add Jandex plugin");
            return;
        }

        String pomContent = Files.readString(pomFile);

        // Check if jandex plugin already exists
        if (pomContent.contains("jandex-maven-plugin")) {
            return;
        }

        // Find the </plugins> closing tag and insert the jandex plugin before it
        String jandexPlugin = """
                        <plugin>
                            <groupId>io.smallrye</groupId>
                            <artifactId>jandex-maven-plugin</artifactId>
                            <version>3.2.2</version>
                            <executions>
                                <execution>
                                    <id>make-index</id>
                                    <phase>process-classes</phase>
                                    <goals>
                                        <goal>jandex</goal>
                                    </goals>
                                </execution>
                            </executions>
                        </plugin>
                """;

        // Insert before </plugins>
        pomContent = pomContent.replace("    </plugins>", jandexPlugin + "    </plugins>");
        Files.writeString(pomFile, pomContent);
        System.out.println("  [CONFIG] Added Jandex Maven plugin to pom.xml for @ConfigMapping discovery");
    }

    private String deriveProjectName(Path projectDir, JsonNode config) throws IOException {
        // Try to use the site title from config
        if (config != null && config.has("title")) {
            String title = config.get("title").asText();
            // Convert title to a valid package name
            String packageName = title
                    .toLowerCase()
                    .replaceAll("[^a-z0-9]+", "")
                    .replaceAll("^[0-9]+", ""); // Remove leading digits
            if (!packageName.isEmpty()) {
                // Avoid io.quarkus namespace to prevent conflicts with Quarkus core packages
                String fullPackage = "quarkus".equals(packageName) ? "io.quarkusio" : "io." + packageName;
                System.out.println("  [CONFIG] Using package name from site title '" + title + "': " + fullPackage);
                return fullPackage;
            }
        }

        // Fall back to directory name
        String dirName = projectDir.toRealPath().getFileName().toString()
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", "")
                .replaceAll("^[0-9]+", ""); // Remove leading digits

        String fullPackage = "quarkusio".equals(dirName) ? "io.quarkusio" : "io." + dirName;
        System.out.println("  [CONFIG] Using package name from directory '" + projectDir.getFileName() + "': "
                + fullPackage);
        return dirName.isEmpty() ? "io.site" : fullPackage;
    }

    static String configMappingPrefix(String projectName) {
        // Strip the leading "io." to get a short prefix like "quarkusio"
        return projectName.startsWith("io.") ? projectName.substring(3) : projectName;
    }
}
