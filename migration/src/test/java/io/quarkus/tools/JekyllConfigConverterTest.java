package io.quarkus.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        assertEquals("true", props.getProperty("quarkus.qute.alt-expr-syntax"));
        assertEquals("yyyy-MM-dd['T'HH:mm:ss][X]", props.getProperty("site.date-format"));
        assertEquals("false", props.getProperty("quarkus.qute.strict-rendering"));
        assertTrue(props.getProperty("quarkus.qute.type-check-excludes").contains("java.lang.Object.*"));
        assertTrue(props.getProperty("quarkus.qute.type-check-excludes")
                .contains("io.quarkiverse.roq.frontmatter.runtime.model.Page.paginator"));
        assertTrue(props.containsKey("quarkus.web-bundler.bundling.external"));
        assertTrue(props.getProperty("quarkus.web-bundler.bundling.external").contains("/assets/*"));
        assertEquals("NOOP", props.getProperty("quarkus.qute.property-not-found-strategy"));
    }

    @Test
    void testSiteUrlFromJekyllConfig() throws IOException {
        String configYaml = """
                url: https://example.com
                """;
        Properties props = converter.createApplicationProperties(
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().readTree(configYaml));
        assertEquals("https://example.com", props.getProperty("site.url"),
                "Jekyll url should map to site.url in application.properties");
    }

    @Test
    void testStrictPropertiesOmitsOutputOriginal() {
        converter.setStrictProperties(true);
        Properties props = converter.createApplicationProperties();
        assertFalse(props.containsKey("quarkus.qute.property-not-found-strategy"),
                "strict mode should not set property-not-found-strategy (defaults to THROW)");
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
                  initial-timeout: 1500
                  more-timeout: 2500
                  min-chars: 2
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertNotNull(siteConfigYaml);
        assertTrue(siteConfigYaml.contains("search:"));
        assertTrue(siteConfigYaml.contains("scriptMode: \"defer\""));
        assertTrue(siteConfigYaml.contains("host: \"https://search.example.com\""));
        assertTrue(siteConfigYaml.contains("scriptPath: \"/search.js\""));
        assertTrue(siteConfigYaml.contains("cachedScriptFile: \"search-cached.js\""));
        assertTrue(siteConfigYaml.contains("initialTimeout: 1500"));
        assertTrue(siteConfigYaml.contains("moreTimeout: 2500"));
        assertTrue(siteConfigYaml.contains("minChars: 2"));
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
    void testSiteConfigYamlWithFeedAndAuthor() throws IOException {
        String configYaml = """
                title: My Site
                author: janedoe
                feed:
                  posts_limit: 50
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertTrue(siteConfigYaml.contains("author: \"janedoe\""));
        assertTrue(siteConfigYaml.contains("feed:"));
        assertTrue(siteConfigYaml.contains("posts_limit: 50"));
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
        assertTrue(propsContent.contains("quarkus.qute.property-not-found-strategy=NOOP"));
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
    void testConvertProjectAddsDescriptionToIndexPage(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                description: Supersonic Subatomic Java
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);
        Files.writeString(tempDir.resolve("index.md"), """
                ---
                layout: index
                title: My Site
                ---
                Hello
                """);

        converter.convertProject(tempDir);

        String indexContent = Files.readString(tempDir.resolve("index.md"));
        assertTrue(indexContent.contains("description: \"Supersonic Subatomic Java\""),
                "Index page should have description from _config.yml: " + indexContent);
    }

    @Test
    void testConvertProjectAddsDescriptionToIndexPageInContentDir(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                description: Supersonic Subatomic Java
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);
        Files.createDirectories(tempDir.resolve("content"));
        Files.writeString(tempDir.resolve("content/index.md"), """
                ---
                layout: index
                title: My Site
                ---
                Hello
                """);

        converter.convertProject(tempDir);

        String indexContent = Files.readString(tempDir.resolve("content/index.md"));
        assertTrue(indexContent.contains("description: \"Supersonic Subatomic Java\""),
                "Index page in content/ should have description from _config.yml: " + indexContent);
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
    void testAutoAuthorPluginAddsCollectionProperties(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                plugins:
                  - jekyll-feed
                  - jekyll-auto-authors
                autopages:
                  authors:
                    enabled: true
                    data: '_data/authors.yaml'
                    layouts:
                      - 'author.html'
                    permalink: '/author/:author/'
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.author.layout=author"));
        assertTrue(propsContent.contains("site.collections.author.from-data.id-key=_key"));
        assertTrue(propsContent.contains("site.collections.author.from-data.name=authors"));
    }

    @Test
    void testAutoAuthorPluginWithCustomDataFile(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                plugins:
                  - jekyll-auto-authors
                autopages:
                  authors:
                    enabled: true
                    data: '_data/contributors.yml'
                    layouts:
                      - 'contributor-page.html'
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.author.layout=contributor-page"));
        assertTrue(propsContent.contains("site.collections.author.from-data.name=contributors"));
        assertTrue(propsContent.contains("site.collections.author.from-data.id-key=_key"));
    }

    @Test
    void testAutoAuthorPluginWithoutAutopagesConfig(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                plugins:
                  - jekyll-auto-authors
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.author.layout=author"));
        assertTrue(propsContent.contains("site.collections.author.from-data.name=authors"));
    }

    @Test
    void testNoAutoAuthorPluginOmitsCollectionProperties(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                plugins:
                  - jekyll-feed
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertFalse(propsContent.contains("site.collections.author"));
    }

    @Test
    void testCollectionsWithOutputTrueGeneratesProperties(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  guides:
                    output: true
                  redirects:
                    output: true
                  versions:
                    output: true
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.guides=true"),
                "Should have guides collection: " + propsContent);
        assertTrue(propsContent.contains("site.collections.redirects=true"),
                "Should have redirects collection: " + propsContent);
        assertTrue(propsContent.contains("site.collections.versions=true"),
                "Should have versions collection: " + propsContent);
    }

    @Test
    void testCollectionsWithoutOutputAreSkipped(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  drafts:
                    output: false
                  internal:
                    foo: bar
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertFalse(propsContent.contains("site.collections.drafts"),
                "Should not have drafts collection: " + propsContent);
        assertFalse(propsContent.contains("site.collections.internal"),
                "Should not have internal collection: " + propsContent);
    }

    @Test
    void testCollectionsSkipsPosts(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  posts:
                    output: true
                  guides:
                    output: true
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertFalse(propsContent.contains("site.collections.posts"),
                "Should not emit posts collection (Roq built-in): " + propsContent);
        assertTrue(propsContent.contains("site.collections.guides=true"),
                "Should have guides collection: " + propsContent);
    }

    @Test
    void testCollectionWithLayoutGeneratesLayoutProperty(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  guides:
                    output: true
                    layout: guide
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.guides=true"),
                "Should have guides collection: " + propsContent);
        assertTrue(propsContent.contains("site.collections.guides.layout=guide"),
                "Should have guides layout: " + propsContent);
    }

    @Test
    void testConvertProjectMovesCollectionDirectories(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  guides:
                    output: true
                  versions:
                    output: true
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        Files.createDirectories(tempDir.resolve("_guides"));
        Files.writeString(tempDir.resolve("_guides/getting-started.adoc"), "= Getting Started");
        Files.createDirectories(tempDir.resolve("_versions/main"));
        Files.writeString(tempDir.resolve("_versions/main/index.md"), "---\ntitle: Main\n---");

        converter.convertProject(tempDir);

        assertTrue(Files.exists(tempDir.resolve("content/guides/getting-started.adoc")),
                "guides should be moved to content/guides");
        assertTrue(Files.exists(tempDir.resolve("content/versions/main/index.md")),
                "versions should be moved to content/versions");
        assertFalse(Files.exists(tempDir.resolve("_guides")),
                "_guides should no longer exist");
        assertFalse(Files.exists(tempDir.resolve("_versions")),
                "_versions should no longer exist");
    }

    @Test
    void testConvertProjectDoesNotMovePostsDirectory(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  posts:
                    output: true
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);
        Files.createDirectories(tempDir.resolve("_posts"));
        Files.writeString(tempDir.resolve("_posts/2024-01-01-hello.md"), "---\ntitle: Hello\n---");

        converter.convertProject(tempDir);

        assertTrue(Files.exists(tempDir.resolve("_posts/2024-01-01-hello.md")),
                "_posts should NOT be moved by the converter (handled by roq-it-jekyll)");
    }

    @Test
    void testCollectionLayoutFromDefaults(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  guides:
                    output: true
                  redirects:
                    output: true
                  versions:
                    output: true
                defaults:
                  - scope:
                      type: guides
                    values:
                      layout: guides
                  - scope:
                      type: versions
                    values:
                      layout: guides
                  - scope:
                      type: redirects
                    values:
                      layout: redirect
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.guides.layout=guides"),
                "Should pick up guides layout from defaults: " + propsContent);
        assertTrue(propsContent.contains("site.collections.redirects.layout=redirect"),
                "Should pick up redirect layout from defaults: " + propsContent);
        assertTrue(propsContent.contains("site.collections.versions.layout=guides"),
                "Should pick up versions layout from defaults: " + propsContent);
    }

    @Test
    void testCollectionLinkFromPermalink(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  guides:
                    output: true
                  versions:
                    output: true
                defaults:
                  - scope:
                      type: guides
                    values:
                      layout: guides
                      permalink: /guides/:name
                  - scope:
                      type: versions
                    values:
                      layout: guides
                      permalink: /version/:path
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.guides.link=/guides/:name"),
                "Should translate guides permalink to link: " + propsContent);
        assertTrue(propsContent.contains("site.collections.versions.link=/version/:dir[1:]/:name"),
                "Should translate versions permalink to link with :dir[1:]/:name: " + propsContent);
    }

    @Test
    void testCollectionLinkTranslatesJekyllPlaceholders(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  posts:
                    output: true
                defaults:
                  - scope:
                      type: posts
                    values:
                      layout: post
                      permalink: /blog/:title/
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertFalse(propsContent.contains("site.collections.posts="),
                "Posts should not be enabled as a collection: " + propsContent);
        assertTrue(propsContent.contains("site.collections.posts.link=/blog/:name/"),
                "Posts permalink should be translated to link: " + propsContent);
    }

    @Test
    void testCollectionLayoutFromConfigOverridesDefaults(@TempDir Path tempDir) throws IOException {
        String configYaml = """
                title: Test Site
                collections:
                  guides:
                    output: true
                    layout: custom
                defaults:
                  - scope:
                      type: guides
                    values:
                      layout: guides
                """;
        Files.writeString(tempDir.resolve("_config.yml"), configYaml);

        converter.convertProject(tempDir);

        String propsContent = Files.readString(tempDir.resolve("config/application.properties"));
        assertTrue(propsContent.contains("site.collections.guides.layout=custom"),
                "Collection-level layout should take precedence over defaults: " + propsContent);
    }

    @Test
    void testSiteConfigYamlWithPublication() throws IOException {
        String configYaml = """
                title: My Site
                publication:
                  group_by: type
                  sort_by: date
                  type_names:
                    article: Article & Blogs
                    podcast: Podcasts
                    video: Videos
                    training: Training
                    book: Books
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertTrue(siteConfigYaml.contains("publication:"));
        assertTrue(siteConfigYaml.contains("group_by: \"type\""));
        assertTrue(siteConfigYaml.contains("type_names:"));
        assertTrue(siteConfigYaml.contains("article: \"Article & Blogs\""));
        assertTrue(siteConfigYaml.contains("video: \"Videos\""));
    }

    @Test
    void testSiteConfigYamlCopiesUnknownTopLevelKeys() throws IOException {
        String configYaml = """
                title: My Site
                baseurl: /blog
                custom_setting: some_value
                nested_custom:
                  key1: val1
                  key2: val2
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertTrue(siteConfigYaml.contains("custom_setting: \"some_value\""),
                "Unknown simple config should be copied: " + siteConfigYaml);
        assertTrue(siteConfigYaml.contains("nested_custom:"),
                "Unknown nested config should be copied: " + siteConfigYaml);
        assertTrue(siteConfigYaml.contains("key1: \"val1\""),
                "Nested values should be preserved: " + siteConfigYaml);
    }

    @Test
    void testSiteConfigYamlDoesNotCopyHandledKeys() throws IOException {
        String configYaml = """
                title: My Site
                url: https://example.com
                collections:
                  posts:
                    output: true
                plugins:
                  - jekyll-feed
                defaults:
                  - scope:
                      type: guides
                    values:
                      layout: guide
                description: A site
                custom: should_be_copied
                """;

        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);

        assertFalse(siteConfigYaml.contains("url:"),
                "url is handled by application.properties, not siteConfig: " + siteConfigYaml);
        assertFalse(siteConfigYaml.contains("collections:"),
                "collections is handled by application.properties: " + siteConfigYaml);
        assertFalse(siteConfigYaml.contains("plugins:"),
                "plugins is handled by application.properties: " + siteConfigYaml);
        assertFalse(siteConfigYaml.contains("defaults:"),
                "defaults is handled by application.properties: " + siteConfigYaml);
        assertFalse(siteConfigYaml.contains("description:"),
                "description goes to index page frontmatter: " + siteConfigYaml);
        assertTrue(siteConfigYaml.contains("custom: \"should_be_copied\""),
                "Unknown keys should be copied: " + siteConfigYaml);
    }

    @Test
    void testCnameWithWhitespace() throws IOException {
        String configYaml = """
                title: Test Site
                """;
        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, "  example.com\n");
        assertTrue(siteConfigYaml.contains("cname: \"example.com\""),
                "CNAME should be trimmed: " + siteConfigYaml);
    }

    @Test
    void testUnsetsShowtitleToPreventDuplicateTitles() {
        Properties props = converter.createApplicationProperties();
        assertEquals("true", props.getProperty("quarkus.asciidoc.attributes.\"!showtitle\""),
                "Should unset showtitle to prevent duplicate title rendering (layout renders page.title, "
                        + "so the AsciiDoc body must not also render it)");
    }

    @Test
    void testAsciidoctorAttributesMappedToQuarkusProperties() throws IOException {
        String configYaml = """
                title: Test Site
                asciidoctor:
                  base_dir: :docdir
                  safe: unsafe
                  attributes:
                    source-highlighter: highlightjs
                    sectanchors: ''
                    icons: font
                    outfilesuffix: ''
                """;
        Properties props = converter.createApplicationProperties(
                new com.fasterxml.jackson.dataformat.yaml.YAMLMapper().readTree(configYaml));
        assertEquals("font", props.getProperty("quarkus.asciidoc.attributes.icons"),
                "Jekyll asciidoctor.attributes.icons should map to quarkus.asciidoc.attributes.icons");
        assertEquals("highlightjs", props.getProperty("quarkus.asciidoc.attributes.source-highlighter"),
                "Jekyll asciidoctor.attributes.source-highlighter should map to quarkus.asciidoc.attributes.source-highlighter");
        assertNull(props.getProperty("quarkus.asciidoc.attributes.sectanchors"),
                "Empty-string attributes should be skipped (SmallRye Config rejects empty map values)");
        assertNull(props.getProperty("quarkus.asciidoc.attributes.outfilesuffix"),
                "Empty-string attributes should be skipped");
    }

    @Test
    void testAsciidoctorAttributesNotCopiedToSiteConfig() throws IOException {
        String configYaml = """
                title: Test Site
                asciidoctor:
                  attributes:
                    icons: font
                custom: keep_me
                """;
        String siteConfigYaml = converter.createSiteConfigYaml(configYaml, null);
        assertFalse(siteConfigYaml.contains("asciidoctor"),
                "asciidoctor config should not appear in siteConfig (handled by application.properties): " + siteConfigYaml);
        assertTrue(siteConfigYaml.contains("custom: \"keep_me\""),
                "Other unknown keys should still be copied: " + siteConfigYaml);
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
        assertTrue(siteConfigYaml.contains("scriptMode: \"defer\""));
    }
}
