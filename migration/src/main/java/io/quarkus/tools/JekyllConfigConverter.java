package io.quarkus.tools;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import static io.quarkus.tools.LiquidToQuteConverter.DATA_NAME;

/**
 * Converts Jekyll _config.yml to Roq application.properties and data/siteConfig.yml.
 * Replaces the bash script logic from roq-it-jekyll lines 61-62, 184-192, and 238-277.
 */
public class JekyllConfigConverter {

    private final YAMLMapper yamlMapper;
    private final ObjectMapper objectMapper;

    public JekyllConfigConverter() {
        this.yamlMapper = new YAMLMapper();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Create application.properties values with standard Roq properties for Jekyll compatibility.
     * Replaces roq-it-jekyll lines 184-192.
     *
     * @return Application properties
     */
    public Properties createApplicationProperties() {
        Properties properties = new Properties();

        // Always enable the alternative expression syntax to reduce overhead of escaping braces
        properties.setProperty("quarkus.qute.alt-expr-syntax", "true");
        // Set a date format with a sensible default for Jekyll.
        properties.setProperty("site.date-format", "yyyy-MM-dd['T'HH:mm:ss][X]");
        // Jekyll templates access arbitrary frontmatter fields that may not exist on every page
        // echo "quarkus.qute.strict-rendering=false" >> ${project_dir}/config/application.properties
        // Exclude type checking for:
        // - Object.* (JsonArray iteration yields Object at build time)
        // - Page.paginator (only on NormalPage subclass, not visible at compile time)
        // - DocumentPage.* (post loop variables access custom frontmatter via data)
        properties.setProperty("quarkus.qute.strict-rendering", "false");
        properties.setProperty("quarkus.qute.type-check-excludes",
                "java.lang.Object.*,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.paginator,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.tags,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.Page.tagsCount,"
                        + "io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage.*");
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
        // Parse the original config
        JsonNode config = yamlMapper.readTree(configYaml);
        
        // Build a new config with selected properties
        Map<String, Object> siteConfig = new LinkedHashMap<>();
        
        // Add CNAME
        siteConfig.put("cname", cnameContent != null ? cnameContent.trim() : "");
        
        // Copy common properties
        copyIfPresent(config, siteConfig, "baseurl");
        copyIfPresent(config, siteConfig, "language");
        copyIfPresent(config, siteConfig, "twitter_username");
        copyIfPresent(config, siteConfig, "github_username");
        
        // Handle nested search config
        if (config.has("search")) {
            JsonNode search = config.get("search");
            Map<String, Object> searchConfig = new LinkedHashMap<>();
            copyIfPresent(search, searchConfig, "script-mode");
            copyIfPresent(search, searchConfig, "host");
            copyIfPresent(search, searchConfig, "script-path");
            copyIfPresent(search, searchConfig, "cached-script-file");
            if (!searchConfig.isEmpty()) {
                siteConfig.put("search", searchConfig);
            }
        }
        
        // Add empty tags array (for Jekyll compatibility)
        siteConfig.put("tags", new Object[0]);
        
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
        Path configDir = projectDir.resolve("config");
        Files.createDirectories(configDir);
        Path propsFile = configDir.resolve("application.properties");

        // Write properties manually — Properties.store() escapes colons in values,
        // which corrupts date format patterns like yyyy-MM-dd['T'HH:mm:ss][X]
        try (Writer writer = Files.newBufferedWriter(propsFile)) {
            Properties props = createApplicationProperties();
            for (String key : props.stringPropertyNames()) {
                writer.write(key + "=" + props.getProperty(key) + "\n");
            }
        }
        
        // Create data/siteConfig.yml
        String siteConfigYaml = createSiteConfigYaml(configYaml, cnameContent);
        Path dataDir = projectDir.resolve("data");
        Files.createDirectories(dataDir);
        Path siteConfigFile = dataDir.resolve(DATA_NAME + ".yml");
        Files.writeString(siteConfigFile, siteConfigYaml);
    }

    private void copyIfPresent(JsonNode source, Map<String, Object> target, String key) {
        if (source.has(key)) {
            JsonNode value = source.get(key);
            if (value.isTextual()) {
                target.put(key, value.asText());
            } else if (value.isNumber()) {
                target.put(key, value.numberValue());
            } else if (value.isBoolean()) {
                target.put(key, value.asBoolean());
            } else if (value.isObject() || value.isArray()) {
                try {
                    target.put(key, objectMapper.convertValue(value, Object.class));
                } catch (IllegalArgumentException e) {
                    // Skip if conversion fails
                }
            }
        }
    }
}
