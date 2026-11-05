package io.quarkiverse.roq.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.migration.engine.Scaffolder;
import io.quarkiverse.roq.migration.engine.SiteAnalysis;
import io.quarkiverse.roq.migration.engine.SiteBuilder;
import io.quarkiverse.roq.migration.engine.SiteMigrator;
import io.quarkiverse.roq.migration.engine.SiteMigrators;
import io.quarkiverse.roq.migration.engine.SiteType;
import io.quarkiverse.roq.migration.engine.SourceSite;
import io.quarkiverse.roq.migration.engine.TargetSite;
import picocli.CommandLine;

class MigrateCommandTest {

    @TempDir
    Path tmp;

    final List<String> calls = new ArrayList<>();
    final StringWriter out = new StringWriter();
    final StringWriter err = new StringWriter();

    /**
     * Moves the posts into content/.
     */
    class JekyllMigrator extends SiteMigrator {

        @Override
        public SiteType type() {
            return SiteType.JEKYLL;
        }

        @Override
        protected void preflight(SourceSite source) {
        }

        @Override
        protected void analyze(SourceSite source, SiteAnalysis analysis) {
            analysis.projectName("blog-website");
        }

        @Override
        protected void restructure(TargetSite site) throws IOException {
            Files.move(site.dir().resolve("_posts"), site.dir().resolve("content"));
        }

        @Override
        protected void convertFrontMatter(TargetSite site) {
        }

        @Override
        protected void convertConfig(TargetSite site) {
        }

        @Override
        protected void convertTemplates(TargetSite site) {
        }

        @Override
        protected void convertContent(TargetSite site) {
        }

        @Override
        protected void convertAssets(TargetSite site) {
        }

        @Override
        protected void generateCode(TargetSite site) {
        }

        @Override
        protected void checkResult(TargetSite site) {
            site.report().todo("Check the posts");
        }
    }

    final Scaffolder scaffolder = site -> {
        calls.add("scaffold " + site.dir().getFileName());
        Files.writeString(site.dir().resolve("pom.xml"), "<project/>");
    };
    final SiteBuilder builder = site -> calls.add("build " + site.dir().getFileName());

    int migrate(SiteMigrators migrators, String... args) {
        CommandLine command = new CommandLine(new MigrateCommand(migrators, scaffolder, builder));
        command.setOut(new PrintWriter(out, true));
        command.setErr(new PrintWriter(err, true));
        return command.execute(args);
    }

    int migrate(String... args) {
        return migrate(new SiteMigrators(Map.of(SiteType.JEKYLL, JekyllMigrator::new)), args);
    }

    Path jekyllSite() throws IOException {
        Path site = Files.createDirectories(tmp.resolve("blog"));
        Files.writeString(site.resolve("_config.yml"), "title: Blog\n");
        Files.createDirectories(site.resolve("_layouts"));
        Files.createDirectories(site.resolve("_posts"));
        Files.writeString(site.resolve("_posts/2026-11-03-hello.md"), "Hello\n");
        return site;
    }

    @Test
    void migratesToADirectoryNextToTheSiteAndBuildsIt() throws IOException {
        Path site = jekyllSite();

        assertThat(migrate(site.toString())).isZero();

        Path migrated = tmp.resolve("blog-roq");
        assertThat(migrated.resolve("content/2026-11-03-hello.md")).hasContent("Hello");
        assertThat(migrated.resolve("ROQ-MIGRATION.md")).content().contains("Check the posts");
        assertThat(site.resolve("_posts/2026-11-03-hello.md")).exists();
        assertThat(calls).containsExactly("scaffold blog-roq", "build blog-roq");
        assertThat(out.toString()).contains("Migrating the Jekyll site in " + site + " to " + migrated,
                "Roq site migrated to " + migrated);
    }

    @Test
    void migratesToTheDirectoryGivenOrInPlace() throws IOException {
        Path site = jekyllSite();
        Path other = tmp.resolve("elsewhere");

        assertThat(migrate(site.toString(), "-o", other.toString(), "--no-build")).isZero();
        assertThat(other.resolve("content/2026-11-03-hello.md")).exists();

        assertThat(migrate(site.toString(), "--in-place", "--no-build")).isZero();
        assertThat(site.resolve("content/2026-11-03-hello.md")).exists();
        assertThat(site.resolve("_posts")).doesNotExist();
        assertThat(out.toString()).contains("Migrating the Jekyll site in " + site + ", in place");

        assertThat(calls).containsExactly("scaffold elsewhere", "scaffold blog");
    }

    @Test
    void refusesBothAnOutputDirectoryAndInPlace() throws IOException {
        Path site = jekyllSite();

        assertThat(migrate(site.toString(), "-o", "x", "--in-place")).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(err.toString()).contains("mutually exclusive");
        assertThat(calls).isEmpty();
    }

    @Test
    void saysWhatItDetectedWhenItCannotMigrateIt() throws IOException {
        Path hugo = Files.createDirectories(tmp.resolve("hugo"));
        Files.writeString(hugo.resolve("hugo.toml"), "");
        Path unknown = Files.createDirectories(tmp.resolve("unknown"));

        assertThat(migrate(SiteMigrators.builtIn(), hugo.toString())).isEqualTo(CommandLine.ExitCode.SOFTWARE);
        assertThat(migrate(SiteMigrators.builtIn(), unknown.toString())).isEqualTo(CommandLine.ExitCode.SOFTWARE);

        // The CLI prints errors on the standard output
        assertThat(out.toString()).contains(hugo + " looks like a Hugo site, but Hugo sites cannot be migrated yet",
                "Could not tell which generator built " + unknown);
        assertThat(tmp.resolve("hugo-roq")).doesNotExist();
    }

    @Test
    void refusesToReplaceTheBuildOfASite() throws IOException {
        Path site = jekyllSite();
        Files.writeString(site.resolve("build.gradle"), "");
        CommandLine command = new CommandLine(new MigrateCommand(
                new SiteMigrators(Map.of(SiteType.JEKYLL, JekyllMigrator::new)), new MigrationScaffolder(), builder));
        command.setOut(new PrintWriter(out, true));

        assertThat(command.execute(site.toString())).isEqualTo(CommandLine.ExitCode.SOFTWARE);
        assertThat(out.toString()).contains(tmp.resolve("blog-roq/build.gradle") + " exists already");
        assertThat(calls).isEmpty();
    }

    @Test
    void refusesASiteThatDoesNotExist() {
        assertThat(migrate(tmp.resolve("missing").toString())).isEqualTo(CommandLine.ExitCode.USAGE);
        assertThat(out.toString()).contains("No such directory: " + tmp.resolve("missing"));
    }
}
