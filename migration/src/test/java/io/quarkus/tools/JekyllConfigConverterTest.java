package io.quarkus.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JekyllConfigConverterTest {

    private JekyllConfigConverter converter;

    @BeforeEach
    void setUp() {
        converter = new JekyllConfigConverter();
    }

    @Test
    void testCreateApplicationProperties() {
        Properties props = converter.createApplicationProperties();

        assertNotNull(props);
        assertTrue(props.containsKey("quarkus.qute.alt-expr-syntax"));
        assertTrue(props.containsKey("site.date-format"));
        assertTrue(props.containsKey("quarkus.qute.strict-rendering"));
        assertTrue(props.containsKey("quarkus.qute.type-check-excludes"));
        assertTrue("true".equals(props.getProperty("quarkus.qute.alt-expr-syntax")));
        assertTrue("yyyy-MM-dd['T'HH:mm:ss][X]".equals(props.getProperty("site.date-format")));
        assertTrue("false".equals(props.getProperty("quarkus.qute.strict-rendering")));
        assertTrue(props.getProperty("quarkus.qute.type-check-excludes").contains("java.lang.Object.*"));
        assertTrue(props.getProperty("quarkus.qute.type-check-excludes")
                .contains("io.quarkiverse.roq.frontmatter.runtime.model.Page.paginator"));
    }

    @Test
    void testSiteConfigYamlBasic() throws IOException {
        String configYaml = """
                title: My Jekyll Site
                baseurl: /blog
                language: en
                twitter_username: johndoe
                """;
        String cname = "example.com";

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, cname);

        assertNotNull(siteConfigYaml);
        assertTrue(siteConfigYaml.contains("cname: \"example.com\""));
        assertTrue(siteConfigYaml.contains("baseurl: \"/blog\""));
        assertTrue(siteConfigYaml.contains("language: \"en\""));
        assertTrue(siteConfigYaml.contains("twitter_username: \"johndoe\""));
        assertTrue(siteConfigYaml.contains("tags: []"));
    }

    @Test
    void testSiteConfigYamlWithNestedSearch() throws IOException {
        String configYaml = """
                title: My Site
                search:
                  script-mode: defer
                  host: https://search.example.com
                  script-path: /search.js
                  cached-script-file: search-cached.js
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertNotNull(siteConfigYaml);
        assertTrue(siteConfigYaml.contains("search:"));
        assertTrue(siteConfigYaml.contains("script-mode: \"defer\""));
        assertTrue(siteConfigYaml.contains("host: \"https://search.example.com\""));
        assertTrue(siteConfigYaml.contains("script-path: \"/search.js\""));
        assertTrue(siteConfigYaml.contains("cached-script-file: \"search-cached.js\""));
    }

    @Test
    void testSiteConfigYamlWithoutCname() throws IOException {
        String configYaml = """
                title: My Site
                baseurl: /blog
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertNotNull(siteConfigYaml);
        assertTrue(siteConfigYaml.contains("cname: \"\""));
    }

    @Test
    void testConvertProjectCreatesFiles(@TempDir Path tempDir) throws IOException {
        // Create _config.yml
        String configYaml = """
                title: Test Site
                description: A test Jekyll site
                baseurl: /test
                language: en
                twitter_username: testuser
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        // Create CNAME
        Files.writeString(tempDir.resolve("CNAME"), "test.example.com");

        // Run conversion
        converter.convertProject(tempDir);

        // Verify application.properties was created
        Path propsFile = tempDir.resolve("config/application.properties");
        assertTrue(Files.exists(propsFile), "application.properties should be created");

        String propsContent = Files.readString(propsFile);
        assertTrue(propsContent.contains("quarkus.qute.alt-expr-syntax=true"));
        assertTrue(propsContent.contains("site.date-format=yyyy-MM-dd['T'HH:mm:ss][X]"));
        assertTrue(propsContent.contains("quarkus.qute.strict-rendering=false"));
        assertTrue(propsContent.contains("quarkus.qute.type-check-excludes="));

        // Verify siteConfig.yml was created
        Path siteConfigFile = tempDir.resolve("data/siteConfig.yml");
        assertTrue(Files.exists(siteConfigFile), "data/siteConfig.yml should be created");

        String siteConfigContent = Files.readString(siteConfigFile);
        assertTrue(siteConfigContent.contains("cname: \"test.example.com\""));
        assertTrue(siteConfigContent.contains("baseurl: \"/test\""));
        assertTrue(siteConfigContent.contains("language: \"en\""));
        assertTrue(siteConfigContent.contains("twitter_username: \"testuser\""));
    }

    @Test
    void testConvertProjectWithoutCname(@TempDir Path tempDir) throws IOException {
        // Create _config.yml only
        String configYaml = """
                title: Test Site
                baseurl: /test
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        // Run conversion (no CNAME file)
        converter.convertProject(tempDir);

        // Verify files were created
        assertTrue(Files.exists(tempDir.resolve("config/application.properties")));
        assertTrue(Files.exists(tempDir.resolve("data/siteConfig.yml")));

        // Verify CNAME is empty in siteConfig
        String siteConfigContent = Files.readString(tempDir.resolve("data/siteConfig.yml"));
        assertTrue(siteConfigContent.contains("cname: \"\""));
    }

    @Test
    void testConvertProjectThrowsExceptionWhenConfigMissing(@TempDir Path tempDir) {
        // No _config.yml file
        IOException exception = assertThrows(IOException.class, () -> {
            converter.convertProject(tempDir);
        });

        assertTrue(exception.getMessage().contains("_config.yml not found"));
    }

    @Test
    void testGithubUsername() throws IOException {
        String configYaml = """
                title: My Site
                github_username: octocat
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertTrue(siteConfigYaml.contains("github_username: \"octocat\""));
    }

    @Test
    void testComplexNestedConfig() throws IOException {
        String configYaml = """
                title: Complex Site
                baseurl: /blog
                url: https://example.com
                language: en
                twitter_username: johndoe
                github_username: johndoe
                search:
                  script-mode: defer
                  host: https://search.example.com
                  script-path: /search.js
                  cached-script-file: search-cached.js
                """;
        String cname = "blog.example.com";

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, cname);

        // Verify siteConfig YAML
        assertTrue(siteConfigYaml.contains("cname: \"blog.example.com\""));
        assertTrue(siteConfigYaml.contains("baseurl: \"/blog\""));
        assertTrue(siteConfigYaml.contains("language: \"en\""));
        assertTrue(siteConfigYaml.contains("twitter_username: \"johndoe\""));
        assertTrue(siteConfigYaml.contains("github_username: \"johndoe\""));
        assertTrue(siteConfigYaml.contains("search:"));
        assertTrue(siteConfigYaml.contains("script-mode: \"defer\""));
    }
}
