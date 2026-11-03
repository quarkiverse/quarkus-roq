package io.quarkiverse.roq.migration.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SiteTypeTest {

    @TempDir
    Path dir;

    /**
     * A minimal but realistic layout for each site type. A path ending in {@code /} is a directory.
     */
    static Stream<Arguments> sites() {
        return Stream.of(
                Arguments.of(SiteType.ROQ, Map.of("pom.xml", "<artifactId>quarkus-roq</artifactId>", "content/", "")),
                Arguments.of(SiteType.JEKYLL, Map.of("_config.yml", "title: Blog", "_layouts/", "", "_posts/", "",
                        "Gemfile", "gem \"jekyll\", \"~> 4.3\"")),
                Arguments.of(SiteType.HEXO, Map.of("_config.yml", "title: Hexo", "scaffolds/", "", "source/_posts/", "",
                        "package.json", "{\"dependencies\": {\"hexo\": \"^7.0.0\"}}")),
                Arguments.of(SiteType.HUGO, Map.of("hugo.toml", "baseURL = 'https://example.org/'", "archetypes/", "",
                        "content/", "", "layouts/", "")),
                Arguments.of(SiteType.ELEVENTY, Map.of("eleventy.config.js", "export default {}",
                        "package.json", "{\"devDependencies\": {\"@11ty/eleventy\": \"^3.0.0\"}}")),
                Arguments.of(SiteType.DOCUSAURUS, Map.of("docusaurus.config.ts", "export default {}",
                        "package.json", "{\"dependencies\": {\"@docusaurus/core\": \"3.5.0\"}}")),
                Arguments.of(SiteType.GATSBY, Map.of("gatsby-config.js", "module.exports = {}",
                        "package.json", "{\"dependencies\": {\"gatsby\": \"^5.0.0\"}}")),
                Arguments.of(SiteType.ASTRO, Map.of("astro.config.mjs", "export default {}",
                        "package.json", "{\"dependencies\": {\"astro\": \"^4.0.0\"}}")),
                Arguments.of(SiteType.NEXTJS, Map.of("next.config.mjs", "export default {}",
                        "package.json", "{\"dependencies\": {\"next\": \"14.0.0\", \"react\": \"18.0.0\"}}")),
                Arguments.of(SiteType.VITEPRESS, Map.of("docs/.vitepress/config.mts", "export default {}",
                        "package.json", "{\"devDependencies\": {\"vitepress\": \"^1.0.0\"}}")),
                Arguments.of(SiteType.VUEPRESS, Map.of("docs/.vuepress/config.js", "module.exports = {}",
                        "package.json", "{\"devDependencies\": {\"vuepress\": \"^2.0.0\"}}")),
                Arguments.of(SiteType.MKDOCS, Map.of("mkdocs.yml", "site_name: Docs", "docs/index.md", "# Docs")),
                Arguments.of(SiteType.SPHINX, Map.of("docs/conf.py", "extensions = ['sphinx.ext.autodoc']",
                        "docs/index.rst", "Docs")),
                Arguments.of(SiteType.ANTORA, Map.of("antora-playbook.yml", "site:\n  title: Docs",
                        "docs/antora.yml", "name: docs")),
                Arguments.of(SiteType.JBAKE, Map.of("jbake.properties", "site.host=http://example.org",
                        "content/", "", "templates/", "")),
                Arguments.of(SiteType.PELICAN, Map.of("pelicanconf.py", "SITENAME = 'Blog'", "content/", "")),
                Arguments.of(SiteType.ZOLA, Map.of("config.toml", "base_url = \"https://example.org\"",
                        "templates/", "", "content/", "", "sass/", "")),
                Arguments.of(SiteType.MIDDLEMAN, Map.of("Gemfile", "gem 'middleman', '~> 4.5'", "config.rb", "",
                        "source/", "")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sites")
    void detectsEachSiteType(SiteType type, Map<String, String> files) throws IOException {
        create(files);

        assertThat(SiteType.detect(dir)).contains(type);
    }

    @Test
    void everySiteTypeHasAFixture() {
        assertThat(sites().map(a -> a.get()[0])).containsExactlyInAnyOrder((Object[]) SiteType.values());
    }

    @Test
    void jekyllWithoutGemfileIsStillJekyll() throws IOException {
        create(Map.of("_config.yml", "title: Blog", "_layouts/", "", "_includes/", ""));

        assertThat(SiteType.detect(dir)).contains(SiteType.JEKYLL);
    }

    @Test
    void hexoIsNotMistakenForJekyll() throws IOException {
        // Both use _config.yml
        create(Map.of("_config.yml", "title: Hexo", "_layouts/", "",
                "package.json", "{\"dependencies\": {\"hexo\": \"^7.0.0\"}}"));

        assertThat(SiteType.detect(dir)).contains(SiteType.HEXO);
    }

    @Test
    void convertedSiteIsDetectedAsRoq() throws IOException {
        // A migrated Jekyll site keeps some Jekyll files around
        create(Map.of("pom.xml", "<artifactId>quarkus-roq</artifactId>", "_config.yml", "title: Blog",
                "Gemfile", "gem \"jekyll\"", "_layouts/", ""));

        assertThat(SiteType.detect(dir)).contains(SiteType.ROQ);
    }

    @Test
    void emptyDirectoryIsNotRecognised() {
        assertThat(SiteType.detect(dir)).isEmpty();
    }

    @Test
    void aLoneConfigYmlIsNotEnough() throws IOException {
        // Could be Jekyll, Hexo, or anything else
        create(Map.of("_config.yml", "title: ?"));

        assertThat(SiteType.detect(dir)).isEmpty();
    }

    @Test
    void ignoresAnUnreadablePackageJson() throws IOException {
        create(Map.of("package.json", "{ not json", "mkdocs.yml", "site_name: Docs"));

        assertThat(SiteType.detect(dir)).contains(SiteType.MKDOCS);
    }

    @Test
    void findsTypesById() {
        assertThat(SiteType.fromId("jekyll")).contains(SiteType.JEKYLL);
        assertThat(SiteType.fromId("NextJS")).contains(SiteType.NEXTJS);
        assertThat(SiteType.fromId("wordpress")).isEmpty();
    }

    private void create(Map<String, String> files) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            Path path = dir.resolve(entry.getKey());
            if (entry.getKey().endsWith("/")) {
                Files.createDirectories(path);
            } else {
                Files.createDirectories(path.getParent());
                Files.writeString(path, entry.getValue());
            }
        }
    }
}
