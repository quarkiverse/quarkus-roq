package io.quarkiverse.roq.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ThemeInitializerTest {

    private static final String POM_WITHOUT_BUILD = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <modelVersion>4.0.0</modelVersion>
                <artifactId>my-site</artifactId>
            </project>
            """;

    private static final String POM_WITH_PLUGINS = """
            <project>
                <artifactId>my-site</artifactId>
                <build>
                    <plugins>
                        <plugin>
                            <artifactId>quarkus-maven-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """;

    private static final String POM_WITH_RESOURCES = """
            <project>
                <artifactId>my-site</artifactId>
                <build>
                    <resources>
                        <resource>
                            <directory>src/main/resources</directory>
                        </resource>
                    </resources>
                </build>
            </project>
            """;

    private static final Path THEME_RESOURCES = Path.of("theme", "src", "main", "resources");

    private Path siteDir;

    @BeforeEach
    void setUp() throws Exception {
        siteDir = Files.createTempDirectory("roq-theme-init-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteDirectory(siteDir.toFile());
    }

    @Test
    void scaffoldsThemeFiles() throws Exception {
        Files.writeString(siteDir.resolve("pom.xml"), POM_WITH_PLUGINS);

        ThemeInitializer.Result result = ThemeInitializer.init(siteDir, "my-theme", "theme", "org.acme");

        Path resources = siteDir.resolve(THEME_RESOURCES);
        assertThat(result.pomUpdated()).isTrue();
        assertThat(siteDir.resolve("theme/pom.xml")).content()
                .contains("<groupId>org.acme</groupId>", "<artifactId>my-theme</artifactId>");
        assertThat(resources.resolve("META-INF/beans.xml")).exists();
        assertThat(resources.resolve("application.properties")).content().contains("#site.theme=my-theme\n");
        assertThat(resources.resolve("templates/partials/my-theme")).isDirectory();
        assertThat(resources.resolve("templates/theme-layouts/my-theme")).isDirectory();
        assertThat(resources.resolve("web/app/my-theme.scss")).exists();
        assertThat(resources.resolve("web/app")).isDirectoryNotContaining("glob:**/.gitkeep");
        assertThat(resources.resolve("templates/.qute")).doesNotExist();
    }

    @Test
    void enablesAltSyntaxForTheJarWhenTheSiteUsesIt() throws Exception {
        Files.writeString(siteDir.resolve("pom.xml"), POM_WITH_PLUGINS);
        Files.createDirectories(siteDir.resolve("config"));
        Files.writeString(siteDir.resolve("config/application.properties"), "quarkus.qute.alt-expr-syntax=true\n");

        ThemeInitializer.Result result = ThemeInitializer.init(siteDir, "my-theme", "theme", "org.acme");

        assertThat(result.altExprSyntax()).isTrue();
        assertThat(siteDir.resolve("theme/src/main/resources/templates/.qute")).content()
                .isEqualTo("alt-expr-syntax=true\n");
    }

    @Test
    void detectsAltSyntaxInYamlConfig() throws Exception {
        Files.createDirectories(siteDir.resolve("src/main/resources"));
        Files.writeString(siteDir.resolve("src/main/resources/application.yml"), """
                quarkus:
                  qute:
                    alt-expr-syntax: true
                """);

        assertThat(ThemeInitializer.isAltExprSyntaxEnabled(siteDir)).isTrue();
    }

    @Test
    void addsResourcesToBuildKeepingDefaultResources() {
        String pom = ThemeInitializer.addThemeResources(POM_WITH_PLUGINS, THEME_RESOURCES);

        assertThat(pom).isEqualTo("""
                <project>
                    <artifactId>my-site</artifactId>
                    <build>
                        <resources>
                            <resource>
                                <directory>src/main/resources</directory>
                            </resource>
                            <resource>
                                <directory>theme/src/main/resources</directory>
                                <excludes>
                                    <!-- Only needed in the packaged theme jar -->
                                    <exclude>META-INF/**</exclude>
                                    <exclude>application.properties</exclude>
                                </excludes>
                            </resource>
                        </resources>
                        <plugins>
                            <plugin>
                                <artifactId>quarkus-maven-plugin</artifactId>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """);
    }

    @Test
    void appendsToExistingResources() {
        String pom = ThemeInitializer.addThemeResources(POM_WITH_RESOURCES, THEME_RESOURCES);

        assertThat(pom).containsOnlyOnce("<directory>src/main/resources</directory>");
        assertThat(pom).containsOnlyOnce("<resources>");
        assertThat(pom).contains("""
                            </resource>
                            <resource>
                                <directory>theme/src/main/resources</directory>
                """);
    }

    @Test
    void createsBuildWhenMissing() {
        String pom = ThemeInitializer.addThemeResources(POM_WITHOUT_BUILD, THEME_RESOURCES);

        assertThat(pom).contains("""
                    <build>
                        <resources>
                            <resource>
                                <directory>src/main/resources</directory>
                """);
        assertThat(pom).endsWith("    </build>\n</project>\n");
    }

    @Test
    void leavesPomUnchangedWhenThemeResourcesAlreadyDeclared() {
        String pom = ThemeInitializer.addThemeResources(POM_WITH_PLUGINS, THEME_RESOURCES);

        assertThat(ThemeInitializer.addThemeResources(pom, THEME_RESOURCES)).isEqualTo(pom);
    }

    @Test
    void refusesExistingDirectory() throws Exception {
        Files.writeString(siteDir.resolve("pom.xml"), POM_WITH_PLUGINS);
        Files.createDirectories(siteDir.resolve("theme"));

        assertThatThrownBy(() -> ThemeInitializer.init(siteDir, "my-theme", "theme", "org.acme"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void refusesInvalidThemeName() throws Exception {
        Files.writeString(siteDir.resolve("pom.xml"), POM_WITH_PLUGINS);

        assertThatThrownBy(() -> ThemeInitializer.init(siteDir, "My Theme", "theme", "org.acme"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void refusesNonMavenSite() {
        assertThatThrownBy(() -> ThemeInitializer.init(siteDir, "my-theme", "theme", "org.acme"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only Maven sites");
    }

    @Test
    void addsStarterLayoutsAndEnablesThemeForNewThemes() throws Exception {
        Files.writeString(siteDir.resolve("pom.xml"), POM_WITH_PLUGINS);
        Files.createDirectories(siteDir.resolve("config"));
        Files.writeString(siteDir.resolve("config/application.properties"), "quarkus.qute.alt-expr-syntax=true\n");

        ThemeInitializer.init(siteDir, "my-theme", "theme", "org.acme", true);

        Path resources = siteDir.resolve(THEME_RESOURCES);
        Path layouts = resources.resolve("templates/theme-layouts/my-theme");
        assertThat(layouts.resolve("default.html")).content()
                .contains("layout: roq-base/default", "class=\"my-theme-header\"", "{=site.title}");
        assertThat(layouts.resolve("page.html")).content().contains("layout: default", "{=page.title}");
        assertThat(layouts.resolve("post.html")).content().contains("{=page.date.longDate}");
        assertThat(resources.resolve("web/app/my-theme.scss")).content().contains(".my-theme-header");
        assertThat(resources.resolve("application.properties")).content()
                .contains("\nsite.theme=my-theme\n")
                .doesNotContain("#site.theme");
    }

    @Test
    void starterLayoutsUseStandardSyntaxWhenTheSiteDoes() throws Exception {
        Files.writeString(siteDir.resolve("pom.xml"), POM_WITH_PLUGINS);

        ThemeInitializer.init(siteDir, "my-theme", "theme", "org.acme", true);

        assertThat(siteDir.resolve("theme/src/main/resources/templates/theme-layouts/my-theme/default.html")).content()
                .contains("{site.title}")
                .doesNotContain("{=");
    }

    @Test
    void enableThemeAddsOrReplacesSiteTheme() throws Exception {
        ThemeInitializer.enableTheme(siteDir, "my-theme");
        assertThat(siteDir.resolve("config/application.properties")).content().isEqualTo("site.theme=my-theme\n");

        Files.writeString(siteDir.resolve("config/application.properties"), "a=b\nsite.theme=other\nc=d");
        ThemeInitializer.enableTheme(siteDir, "my-theme");
        assertThat(siteDir.resolve("config/application.properties")).content()
                .isEqualTo("a=b\nsite.theme=my-theme\nc=d");
    }

    @Test
    void createsPreviewSiteForNewTheme() throws Exception {
        Path previewDir = siteDir.resolve("my-theme");

        ThemeInitializer.initWithPreviewSite(previewDir, "my-theme", "theme", "org.acme", "999-SNAPSHOT");

        assertThat(previewDir.resolve("pom.xml")).content()
                .contains("<artifactId>my-theme-site</artifactId>", "<directory>theme/src/main/resources</directory>");
        assertThat(previewDir.resolve("config/application.properties")).content().contains("site.theme=my-theme");
        assertThat(previewDir.resolve("theme/src/main/resources/templates/.qute")).exists();
        assertThat(previewDir.resolve("theme/src/main/resources/templates/theme-layouts/my-theme/default.html")).exists();
    }

    @Test
    void refusesExistingPreviewSiteDirectory() throws Exception {
        Files.createDirectories(siteDir.resolve("my-theme"));

        assertThatThrownBy(() -> ThemeInitializer.initWithPreviewSite(siteDir.resolve("my-theme"), "my-theme", "theme",
                "org.acme", "999-SNAPSHOT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }
}
