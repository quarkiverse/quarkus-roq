package io.quarkus.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Tests the full Jekyll-to-Roq conversion by running the {@code roq-it-jekyll}
 * script on the Jekyll fixture and verifying the output.
 *
 * Uses the same static fixture as {@link JekyllMigrationE2EIT}; see that class
 * for why we use a checked-in fixture rather than {@code jekyll new}.
 *
 * The converted output is written to {@code target/converted-jekyll-site/} so that
 * {@link JekyllMigrationE2EIT} (run later in the failsafe integration-test phase)
 * can point {@code quarkus.roq.dir} at it. The directory must exist before Quarkus
 * augmentation runs, so producing it during the surefire test phase is the simplest
 * way to ensure that.
 */
@DisabledOnOs(value = OS.WINDOWS, disabledReason = "Jekyll migration requires Unix-like environment with bash. "
        + "The roq-it-jekyll script uses bash and Unix tools. Windows users should use WSL2 if needed.")
class JekyllToRoqConversionTest {

    static final Path WORK_DIR = JekyllConversionTestResource.WORK_DIR;

    @BeforeAll
    static void runConversion() throws Exception {
        if (Files.exists(WORK_DIR)) {
            JekyllConversionTestResource.deleteRecursively(WORK_DIR);
        }
        Files.createDirectories(WORK_DIR);
        JekyllConversionTestResource.copyFixture(WORK_DIR);

        int exitCode = JekyllConversionTestResource.runMigrationScriptForExitCode(WORK_DIR);
        assertEquals(0, exitCode, "roq-it-jekyll should succeed");
    }

    // --- Frontmatter conversion ---

    @Test
    void indexFrontmatterIsMigrated() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("content/index.md"));
        assertThat(content)
                .contains("paginate:")
                .contains("collection: posts")
                .contains("size: 5")
                .contains("link: index/page/:page")
                .doesNotContain("pagination:");
    }

    @Test
    void aboutPermalinkIsConvertedToLink() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("content/about.md"));
        assertThat(content)
                .contains("link: /company/about/")
                .doesNotContain("permalink:");
    }

    @Test
    void postContentIsPreserved() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("content/posts/2024-01-15-hello-world.md"));
        assertThat(content)
                .contains("title: \"Hello World\"")
                .contains("Welcome to the blog");
    }

    // --- Template conversion ---

    @Test
    void defaultLayoutUsesQuteSyntax() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("templates/layouts/default.html"));
        assertThat(content)
                .as("Variables should use Qute expression syntax")
                .contains("{=page.title.trim().raw}")
                .contains("{=site.title.raw}")
                .as("Includes should use Qute syntax with partials/ path")
                .contains("{#include partials/header.html")
                .as("No Liquid syntax should remain")
                .doesNotContain("{{")
                .doesNotContain("{%");
    }

    @Test
    void postLayoutConditionalsAndDateFilterConverted() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("templates/layouts/post.html"));
        assertThat(content)
                .as("Conditionals should use Qute syntax")
                .contains("{#if page.data.author}")
                .contains("{/if}")
                .as("Date filter should be converted to Java format")
                .contains(".format('MMM d, yyyy')")
                .doesNotContain("| date:");
    }

    @Test
    void homeLayoutLoopsConverted() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("templates/layouts/home.html"));
        assertThat(content)
                .as("Loops should use Qute syntax")
                .contains("{#for post in site.collections.get('posts').orEmpty}")
                .contains("{/for}")
                .doesNotContain("{% for")
                .doesNotContain("{% endfor");
    }

    // --- Partials conversion ---

    @Test
    void headerPartialUsesQuteSyntax() throws IOException {
        String content = Files.readString(WORK_DIR.resolve("templates/partials/header.html"));
        assertThat(content)
                .contains("{=site.title")
                .doesNotContain("{{");
    }

    // --- Directory structure ---

    @Test
    void outputDirectoryStructureIsCorrect() {
        assertThat(WORK_DIR.resolve("content/posts")).isDirectory();
        assertThat(WORK_DIR.resolve("templates/layouts")).isDirectory();
        assertThat(WORK_DIR.resolve("templates/partials")).isDirectory();
        assertThat(WORK_DIR.resolve("web/main.css")).exists();
        assertThat(WORK_DIR.resolve("config/application.properties")).exists();
    }

    // --- Config ---

    @Test
    void applicationPropertiesContainsStrictRenderingFalse() throws IOException {
        String config = Files.readString(WORK_DIR.resolve("config/application.properties"));
        assertThat(config)
                .contains("quarkus.qute.strict-rendering=false");
    }
}
