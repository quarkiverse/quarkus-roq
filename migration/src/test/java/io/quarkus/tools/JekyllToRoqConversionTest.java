package io.quarkus.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

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
class JekyllToRoqConversionTest {

    private static final String[] FIXTURE_FILES = {
            "_config.yml",
            "_includes/header.html",
            "_layouts/default.html",
            "_layouts/home.html",
            "_layouts/page.html",
            "_layouts/post.html",
            "_posts/2024-01-15-hello-world.md",
            "_site/index.html",
            "_site/assets/css/main.css",
            "assets/css/main.css",
            "Gemfile",
            "Gemfile.lock",
            "index.md",
            "about.md"
    };

    static final Path WORK_DIR = Path.of("target/converted-jekyll-site");

    @Test
    void roqItJekyllProducesValidRoqSite() throws Exception {
        Path workDir = WORK_DIR;
        if (Files.exists(workDir)) {
            deleteRecursively(workDir);
        }
        Files.createDirectories(workDir);
        copyFixture(workDir);

        int exitCode = runMigrationScript(workDir);
        assertEquals(0, exitCode, "roq-it-jekyll should succeed");

        // --- Verify frontmatter conversion ---

        String indexContent = Files.readString(workDir.resolve("content/index.md"));
        assertThat(indexContent)
                .contains("paginate:")
                .contains("collection: posts")
                .contains("size: 5")
                .contains("link: index/page/:page")
                .doesNotContain("pagination:");

        String aboutContent = Files.readString(workDir.resolve("content/about.md"));
        assertThat(aboutContent)
                .contains("aliases: /company/about/")
                .doesNotContain("permalink:");

        String postContent = Files.readString(workDir.resolve("content/posts/2024-01-15-hello-world.md"));
        assertThat(postContent)
                .contains("title: \"Hello World\"")
                .contains("Welcome to the blog");

        // --- Verify template conversion ---

        String defaultLayout = Files.readString(workDir.resolve("templates/layouts/default.html"));
        assertThat(defaultLayout)
                .as("Variables should use Qute expression syntax")
                .contains("{=page.title.trim()}")
                .contains("{=site.title}")
                .as("Includes should use Qute syntax with partials/ path")
                .contains("{#include partials/header.html /}")
                .as("No Liquid syntax should remain")
                .doesNotContain("{{")
                .doesNotContain("{%");

        String postLayout = Files.readString(workDir.resolve("templates/layouts/post.html"));
        assertThat(postLayout)
                .as("Conditionals should use Qute syntax")
                .contains("{#if page.data.author??}")
                .contains("{/if}")
                .as("Date filter should be converted to Java format")
                .contains(".format('MMM d, yyyy')")
                .doesNotContain("| date:");

        String homeLayout = Files.readString(workDir.resolve("templates/layouts/home.html"));
        assertThat(homeLayout)
                .as("Loops should use Qute syntax")
                .contains("{#for post in site.collections.get('posts').orEmpty}")
                .contains("{/for}")
                .doesNotContain("{% for")
                .doesNotContain("{% endfor");

        // --- Verify partials conversion ---

        String headerPartial = Files.readString(workDir.resolve("templates/partials/header.html"));
        assertThat(headerPartial)
                .contains("{=site.title")
                .doesNotContain("{{");

        // --- Verify directory structure ---

        assertThat(workDir.resolve("content/posts")).isDirectory();
        assertThat(workDir.resolve("templates/layouts")).isDirectory();
        assertThat(workDir.resolve("templates/partials")).isDirectory();
        assertThat(workDir.resolve("public/css/main.css")).exists();
        assertThat(workDir.resolve("config/application.properties")).exists();

        // --- Verify config ---

        String config = Files.readString(workDir.resolve("config/application.properties"));
        assertThat(config)
                .contains("quarkus.qute.strict-rendering=false");
    }

    private void copyFixture(Path target) throws IOException {
        for (String file : FIXTURE_FILES) {
            Path dest = target.resolve(file);
            Files.createDirectories(dest.getParent());
            try (InputStream is = Objects.requireNonNull(
                    getClass().getResourceAsStream("/jekyll-site/" + file),
                    "Missing fixture: /jekyll-site/" + file)) {
                Files.copy(is, dest);
            }
        }
        Files.createDirectories(target.resolve("_data"));
    }

    private int runMigrationScript(Path siteDir) throws Exception {
        Path scriptPath = findScript();

        ProcessBuilder pb = new ProcessBuilder("bash", scriptPath.toString(), siteDir.toAbsolutePath().toString());
        pb.environment().put("BATCH_MODE", "true");
        pb.redirectErrorStream(true);

        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(5, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            System.err.println("SCRIPT TIMED OUT. Output:\n" + output);
            return -1;
        }
        if (process.exitValue() != 0) {
            System.err.println("SCRIPT FAILED (exit " + process.exitValue() + "). Output:\n" + output);
        }
        return process.exitValue();
    }

    private static void deleteRecursively(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null)
                    throw exc;
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private Path findScript() {
        Path script = Path.of("roq-it-jekyll");
        if (Files.exists(script)) {
            return script;
        }
        script = Path.of("migration/roq-it-jekyll");
        if (Files.exists(script)) {
            return script;
        }
        throw new RuntimeException("Cannot find roq-it-jekyll script");
    }
}
