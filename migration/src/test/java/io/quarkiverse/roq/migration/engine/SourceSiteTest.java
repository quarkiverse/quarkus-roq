package io.quarkiverse.roq.migration.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceSiteTest {

    @TempDir
    Path dir;

    SourceSite site;

    @BeforeEach
    void createSite() throws IOException {
        Files.createDirectories(dir.resolve("_posts"));
        Files.writeString(dir.resolve("_config.yml"), "title: Test");
        site = new SourceSite(dir);
    }

    @Test
    void checksFilesAndDirectories() {
        assertThat(site.isFile("_config.yml")).isTrue();
        assertThat(site.isFile("_posts")).isFalse();
        assertThat(site.isDirectory("_posts")).isTrue();
        assertThat(site.isDirectory("_layouts")).isFalse();
        assertThat(site.anyFile("missing.yml", "_config.yml")).isTrue();
        assertThat(site.anyFile("missing.yml", "_posts")).isFalse();
    }

    @Test
    void checksFileContents() {
        assertThat(site.contains("_config.yml", "title:")).isTrue();
        assertThat(site.contains("_config.yml", "baseurl")).isFalse();
        assertThat(site.contains("missing.yml", "title:")).isFalse();
        assertThat(site.contains("_posts", "title:")).isFalse();
    }

    @Test
    void readsNpmDependencies() throws IOException {
        assertThat(site.npmDependency("hexo")).isFalse();

        Files.writeString(dir.resolve("package.json"),
                "{\"dependencies\": {\"hexo\": \"7\"}, \"devDependencies\": {\"@11ty/eleventy\": \"3\"}}");

        assertThat(site.npmDependency("hexo")).isTrue();
        assertThat(site.npmDependency("@11ty/eleventy")).isTrue();
        assertThat(site.npmDependency("next")).isFalse();
    }

    @Test
    void treatsAnUnreadablePackageJsonAsNoDependencies() throws IOException {
        Files.writeString(dir.resolve("package.json"), "{ not json");

        assertThat(site.npmDependency("hexo")).isFalse();
    }

    @Test
    void refusesPathsOutsideTheSite() {
        assertThatThrownBy(() -> site.isFile("../elsewhere"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsFiles() throws Exception {
        assertThat(site.name()).isEqualTo(dir.getFileName().toString());
        assertThat(site.exists("_posts")).isTrue();
        assertThat(site.exists("_layouts")).isFalse();
        assertThat(site.readString("_config.yml")).isEqualTo("title: Test");
    }

    @Test
    void reportsMissingFilesAsMigrationErrors() {
        assertThatThrownBy(() -> site.readString("nope.yml"))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("nope.yml");
    }

    @Test
    void listsFilesRelativeToTheSiteSkippingIgnoredDirectories() throws Exception {
        addPostsAndNodeModules();

        assertThat(site.files("")).containsExactly("_config.yml", "_posts/2024/a.md", "_posts/b.adoc");
        assertThat(site.files("_posts")).containsExactly("_posts/2024/a.md", "_posts/b.adoc");
        assertThat(site.files("missing")).isEmpty();
    }

    @Test
    void listsChildren() throws Exception {
        addPostsAndNodeModules();

        assertThat(site.children("")).containsExactly("_config.yml", "_posts", "node_modules");
        assertThat(site.children("_config.yml")).isEmpty();
    }

    private void addPostsAndNodeModules() throws IOException {
        Files.createDirectories(dir.resolve("_posts/2024"));
        Files.createDirectories(dir.resolve("node_modules/pkg"));
        Files.writeString(dir.resolve("_posts/2024/a.md"), "a");
        Files.writeString(dir.resolve("_posts/b.adoc"), "b");
        Files.writeString(dir.resolve("node_modules/pkg/index.js"), "x");
    }
}
