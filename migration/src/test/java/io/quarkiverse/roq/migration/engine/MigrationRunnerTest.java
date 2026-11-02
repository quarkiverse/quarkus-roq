package io.quarkiverse.roq.migration.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.quarkiverse.roq.migration.engine.MigrationRunner.MigrationResult;

class MigrationRunnerTest {

    @TempDir
    Path tmp;

    Path source;
    final List<String> calls = new ArrayList<>();
    final List<String> output = new ArrayList<>();
    final List<String> warnings = new ArrayList<>();
    final MigrationOutput recordingOutput = new MigrationOutput() {
        @Override
        public void info(String message) {
            output.add(message);
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }
    };
    final Scaffolder scaffolder = site -> calls.add("scaffold");
    final SiteBuilder builder = site -> calls.add("build");

    @BeforeEach
    void createSource() throws IOException {
        source = tmp.resolve("my-site");
        Files.createDirectories(source.resolve("_posts"));
        Files.writeString(source.resolve("_config.yml"), "title: My Site\n");
        Files.writeString(source.resolve("_posts/hello.md"), "hello");
    }

    /**
     * Records the order its stages are called in, and moves {@code _posts} into {@code content/}.
     */
    class RecordingMigrator extends SiteMigrator {

        @Override
        public SiteType type() {
            return SiteType.JEKYLL;
        }

        @Override
        protected void preflight(SourceSite source) throws MigrationException {
            calls.add("preflight");
        }

        @Override
        protected void analyze(SourceSite source, SiteAnalysis analysis) throws MigrationException {
            calls.add("analyze");
            analysis.projectName(source.name() + "-website");
        }

        @Override
        protected void restructure(TargetSite site) throws Exception {
            calls.add("restructure");
            Files.createDirectories(site.dir().resolve("content"));
            Files.move(site.dir().resolve("_posts"), site.dir().resolve("content/posts"));
        }

        @Override
        protected void convertFrontMatter(TargetSite site) {
            calls.add("frontmatter");
        }

        @Override
        protected void convertConfig(TargetSite site) {
            calls.add("config");
        }

        @Override
        protected void convertTemplates(TargetSite site) {
            calls.add("templates");
        }

        @Override
        protected void convertContent(TargetSite site) throws Exception {
            calls.add("content");
        }

        @Override
        protected void convertAssets(TargetSite site) throws Exception {
            calls.add("assets");
        }

        @Override
        protected void generateCode(TargetSite site) {
            calls.add("code");
        }

        @Override
        protected void checkResult(TargetSite site) {
            calls.add("check");
        }
    }

    MigrationOptions inPlace() {
        return new MigrationOptions(source, true, recordingOutput);
    }

    MigrationOptions into(Path outputDir) {
        return new MigrationOptions(outputDir, true, recordingOutput);
    }

    MigrationResult run(SiteMigrator migrator, MigrationOptions options) throws MigrationException {
        return new MigrationRunner(migrator, source, scaffolder, builder, options).run();
    }

    MigrationResult run(MigrationOptions options) throws MigrationException {
        return run(new RecordingMigrator(), options);
    }

    @Test
    void runsStagesInOrder() throws Exception {
        run(inPlace());

        assertThat(calls).containsExactly("preflight", "analyze", "scaffold", "restructure", "frontmatter", "config",
                "templates", "content", "assets", "code", "check", "build");
    }

    @Test
    void printsAHeadingPerStage() throws Exception {
        run(inPlace());

        assertThat(output).filteredOn(l -> l.startsWith("[")).extracting(l -> l.substring(1, l.indexOf(']')))
                .containsExactly("preflight", "analyze", "scaffold", "restructure", "frontmatter", "config",
                        "templates", "content", "assets", "code", "check", "build");
    }

    @Test
    void sharesTheAnalysisWithLaterStages() throws Exception {
        MigrationResult result = run(inPlace());

        assertThat(result.analysis().projectName()).isEqualTo("my-site-website");
    }

    @Test
    void migratesInPlaceWhenTheOutputIsTheSource() throws Exception {
        MigrationResult result = run(inPlace());

        assertThat(result.siteDir()).isEqualTo(source.toAbsolutePath());
        assertThat(source.resolve("content/posts/hello.md")).exists();
        assertThat(source.resolve("_posts")).doesNotExist();
    }

    @Test
    void copiesToOutputDirectoryAndLeavesSourceUntouched() throws Exception {
        Files.createDirectories(source.resolve(".git"));
        Files.writeString(source.resolve(".git/HEAD"), "ref");
        Files.createDirectories(source.resolve("_site"));
        Files.createDirectories(source.resolve("node_modules/pkg"));
        Path out = tmp.resolve("my-site-roq");

        MigrationResult result = run(into(out));

        assertThat(result.siteDir()).isEqualTo(out.toAbsolutePath());
        assertThat(out.resolve("content/posts/hello.md")).exists();
        assertThat(out.resolve("_config.yml")).exists();
        assertThat(out.resolve(".git")).doesNotExist();
        assertThat(out.resolve("_site")).doesNotExist();
        assertThat(out.resolve("node_modules")).doesNotExist();
        assertThat(source.resolve("_posts/hello.md")).exists();
        assertThat(source.resolve("content")).doesNotExist();
    }

    @Test
    void acceptsAnExistingEmptyOutputDirectory() throws Exception {
        Path out = Files.createDirectories(tmp.resolve("empty"));

        run(into(out));

        assertThat(out.resolve("content/posts/hello.md")).exists();
    }

    @Test
    void noBuildSkipsTheBuilder() throws Exception {
        run(new MigrationOptions(source, false, recordingOutput));

        assertThat(calls).last().isEqualTo("check");
        assertThat(output).noneMatch(l -> l.startsWith("[build]"));
    }

    @Test
    void refusesNonEmptyOutputDirectory() throws IOException {
        Path out = Files.createDirectories(tmp.resolve("out"));
        Files.writeString(out.resolve("keep.txt"), "mine");

        assertThatThrownBy(() -> run(into(out)))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("not empty");
        assertThat(calls).isEmpty();
    }

    @Test
    void refusesOutputInsideTheSource() {
        Path out = source.resolve("converted");

        assertThatThrownBy(() -> run(into(out)))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("inside");
    }

    @Test
    void refusesOutputContainingTheSource() {
        assertThatThrownBy(() -> run(into(tmp)))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("contain");
    }

    @Test
    void preflightFailureStopsBeforeAnythingIsWritten() {
        Path out = tmp.resolve("out");
        SiteMigrator migrator = new RecordingMigrator() {
            @Override
            protected void preflight(SourceSite source) throws MigrationException {
                throw new MigrationException("Not a Jekyll site");
            }
        };

        assertThatThrownBy(() -> run(migrator, into(out)))
                .isInstanceOf(MigrationException.class)
                .hasMessage("Not a Jekyll site");
        assertThat(out).doesNotExist();
        assertThat(calls).isEmpty();
    }

    @Test
    void analyzeFailureStopsBeforeAnythingIsWritten() {
        Path out = tmp.resolve("out");
        SiteMigrator migrator = new RecordingMigrator() {
            @Override
            protected void analyze(SourceSite source, SiteAnalysis analysis) throws MigrationException {
                source.readString("missing.yml");
            }
        };

        assertThatThrownBy(() -> run(migrator, into(out)))
                .isInstanceOf(MigrationException.class)
                .hasMessageContaining("missing.yml");
        assertThat(out).doesNotExist();
    }

    @Test
    void stageFailureNamesTheStage() {
        SiteMigrator migrator = new RecordingMigrator() {
            @Override
            protected void convertAssets(TargetSite site) throws Exception {
                throw new IOException("disk full");
            }
        };

        assertThatThrownBy(() -> run(migrator, inPlace()))
                .isInstanceOf(MigrationException.class)
                .hasMessage("Migration stage 'assets' failed: disk full")
                .hasCauseInstanceOf(IOException.class);
        assertThat(calls).doesNotContain("code", "build");
    }

    @Test
    void collectsWarningsWithTheirStage() throws Exception {
        SiteMigrator migrator = new RecordingMigrator() {
            @Override
            protected void convertContent(TargetSite site) {
                site.report().warn("newsletter/ looks like content");
            }
        };

        MigrationResult result = run(migrator, inPlace());

        assertThat(result.warnings()).singleElement()
                .satisfies(w -> {
                    assertThat(w.stage()).isEqualTo("content");
                    assertThat(w.message()).isEqualTo("newsletter/ looks like content");
                });
        assertThat(warnings).containsExactly("  newsletter/ looks like content");
    }
}
