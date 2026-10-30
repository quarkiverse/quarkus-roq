package io.quarkiverse.roq.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import picocli.CommandLine;

/**
 * Runs {@code roq theme init} through picocli, in a test directory given with {@code --cli-test-dir}.
 */
public class ThemeCommandTest {

    private static final String ROQ_SITE_POM = """
            <project>
                <artifactId>my-site</artifactId>
                <dependencies>
                    <dependency>
                        <groupId>io.quarkiverse.roq</groupId>
                        <artifactId>quarkus-roq</artifactId>
                    </dependency>
                </dependencies>
            </project>
            """;

    private Path dir;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("roq-theme-command-test");
    }

    @AfterEach
    void tearDown() throws Exception {
        FileUtils.deleteDirectory(dir.toFile());
    }

    @Test
    void addsThemeToExistingSite() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), ROQ_SITE_POM);

        int exitCode = run("my-theme");

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        assertThat(dir.resolve("theme/src/main/resources/templates/partials/my-theme")).isDirectory();
        assertThat(dir.resolve("pom.xml")).content().contains("<directory>theme/src/main/resources</directory>");
        assertThat(out.toString()).contains("Roq theme created in", "Next, move the theme files from the site");
    }

    @Test
    void createsPreviewSiteOutsideASite() throws Exception {
        int exitCode = run("my-theme", "--roq-version", "999-SNAPSHOT");

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.OK);
        Path previewSite = dir.resolve("my-theme");
        assertThat(previewSite.resolve("pom.xml")).content().contains("<artifactId>my-theme-site</artifactId>");
        assertThat(previewSite.resolve("theme/src/main/resources/templates/theme-layouts/my-theme/default.html")).exists();
        assertThat(out.toString()).contains("Roq theme created with a preview site", "roq start");
    }

    @Test
    void reportsErrors() throws Exception {
        Files.writeString(dir.resolve("pom.xml"), ROQ_SITE_POM);
        Files.createDirectories(dir.resolve("theme"));

        int exitCode = run("my-theme");

        assertThat(exitCode).isEqualTo(CommandLine.ExitCode.USAGE);
        // Like the Quarkus CLI, errors are printed on the standard output
        assertThat(out.toString()).contains("[ERROR]", "Directory 'theme' already exists");
    }

    private int run(String... args) {
        List<String> commandLine = new ArrayList<>(List.of("theme", "init"));
        commandLine.addAll(List.of(args));
        commandLine.addAll(List.of("--cli-test", "--cli-test-dir", dir.toString()));
        return new CommandLine(new RoqMain())
                .setOut(new PrintWriter(out, true))
                .setErr(new PrintWriter(err, true))
                .execute(commandLine.toArray(String[]::new));
    }
}
