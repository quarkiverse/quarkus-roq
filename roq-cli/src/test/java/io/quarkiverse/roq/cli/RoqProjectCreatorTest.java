package io.quarkiverse.roq.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RoqProjectCreatorTest {

    private static final String ROQ_VERSION = "999-SNAPSHOT";

    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("roq-cli-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteDirectory(tempDir.toFile());
    }

    @Test
    void createDefaultSite() throws Exception {
        Path projectDir = createProject("my-site");

        assertProjectStructure(projectDir);
        assertThat(projectDir.resolve("config/application.properties")).exists();
        assertPomContains(projectDir, "quarkus-roq-theme-default");
    }

    @Test
    void createWithResumeTheme() throws Exception {
        Path projectDir = createProject("resume-site", "theme:resume");

        assertProjectStructure(projectDir);
        assertPomContains(projectDir, "quarkus-roq-theme-resume");
        assertThat(projectDir.resolve("data/bio.yml")).exists();
    }

    @Test
    void createWithLinktreeTheme() throws Exception {
        Path projectDir = createProject("linktree-site", "theme:linktree");

        assertProjectStructure(projectDir);
        assertPomContains(projectDir, "quarkus-roq-theme-linktree");
        assertThat(projectDir.resolve("data/profile.yml")).exists();
    }

    @Test
    void createWithPlugins() throws Exception {
        Path projectDir = createProject("plugin-site", "plugin:markdown", "plugin:tagging", "plugin:sitemap");

        assertProjectStructure(projectDir);
        assertPomContains(projectDir, "quarkus-roq-plugin-markdown");
        assertPomContains(projectDir, "quarkus-roq-plugin-tagging");
        assertPomContains(projectDir, "quarkus-roq-plugin-sitemap");
    }

    @Test
    void configContainsAltExprSyntax() throws Exception {
        Path projectDir = createProject("config-site");

        assertThat(projectDir.resolve("config/application.properties"))
                .content().contains("quarkus.qute.alt-expr-syntax=true");
    }

    @Test
    void noSrcDirectoryWhenNoJavaCode() throws Exception {
        Path projectDir = createProject("nosrc-site");

        assertThat(projectDir.resolve("src")).doesNotExist();
    }

    @Test
    void resolveExtensionShorthand() {
        assertThat(RoqProjectCreator.resolveExtension("theme:default"))
                .isEqualTo("io.quarkiverse.roq:quarkus-roq-theme-default");
        assertThat(RoqProjectCreator.resolveExtension("plugin:tagging"))
                .isEqualTo("io.quarkiverse.roq:quarkus-roq-plugin-tagging");
        assertThat(RoqProjectCreator.resolveExtension("web:sass"))
                .isEqualTo("io.quarkiverse.web-bundler:quarkus-web-bundler-sass");
        assertThat(RoqProjectCreator.resolveExtension("theme:base"))
                .isNull();
        assertThat(RoqProjectCreator.resolveExtension("io.quarkus:quarkus-rest"))
                .isEqualTo("io.quarkus:quarkus-rest");
    }

    private Path createProject(String name, String... extensions) throws Exception {
        Path projectDir = tempDir.resolve(name);
        boolean success = new RoqProjectCreator(projectDir, name)
                .roqVersion(ROQ_VERSION)
                .extensions(extensions.length > 0 ? List.of(extensions) : null)
                .create();
        assertThat(success).isTrue();
        return projectDir;
    }

    private void assertProjectStructure(Path projectDir) {
        assertThat(projectDir.resolve("pom.xml")).exists();
        assertThat(projectDir.resolve("README.md")).exists();
        assertThat(projectDir.resolve("content")).isDirectory();
        assertThat(projectDir.resolve("public/images/favicon.ico")).exists();
    }

    private void assertPomContains(Path projectDir, String artifactId) throws Exception {
        assertThat(projectDir.resolve("pom.xml"))
                .content().contains(artifactId);
    }
}
