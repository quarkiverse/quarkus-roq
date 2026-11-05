package io.quarkiverse.roq.migration.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

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
    final List<String> references = new ArrayList<>();
    final SiteBuilder builder = site -> {
        calls.add("build");
        // Like roq generate: a page per directory, and a 404 page
        for (String page : List.of("index.html", "about/index.html", "posts/hello/index.html", "404.html")) {
            Path file = site.dir().resolve(SiteBuilder.GENERATED_SITE).resolve(page);
            Files.createDirectories(file.getParent());
            Files.writeString(file, "<html></html>");
        }
    };

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

        @Override
        protected void validate(TargetSite site, ReferenceSite reference) throws Exception {
            calls.add("validate");
            references.add(reference == null ? "none" : reference.url() + " " + reference.missingPages());
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
                "templates", "content", "assets", "code", "check", "build", "validate");
    }

    @Test
    void printsAHeadingPerStage() throws Exception {
        run(inPlace());

        assertThat(output).filteredOn(l -> l.startsWith("[")).extracting(l -> l.substring(1, l.indexOf(']')))
                .containsExactly("preflight", "analyze", "scaffold", "restructure", "frontmatter", "config",
                        "templates", "content", "assets", "code", "check", "build", "validate");
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

        assertThat(calls).last().isEqualTo("validate");
        assertThat(output).noneMatch(l -> l.startsWith("[build]"));
    }

    /**
     * Serves the given paths, and answers 404 for the others.
     */
    static HttpServer referenceSite(String... paths) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            boolean found = List.of(paths).contains(exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(found ? 200 : 404, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    static URI url(HttpServer server) {
        return URI.create("http://localhost:" + server.getAddress().getPort());
    }

    @Test
    void comparesTheBuiltPagesWithTheReferenceSite() throws Exception {
        HttpServer server = referenceSite("/", "/about/");
        try {
            run(new MigrationOptions(source, true, recordingOutput, url(server)));
        } finally {
            server.stop(0);
        }

        // Not the 404 page
        assertThat(references).containsExactly(url(server) + " [/posts/hello/ (404)]");
        assertThat(output).contains("  Comparing the site with " + url(server));
    }

    @Test
    void comparesWithTheReferenceSiteTheMigratorFound() throws Exception {
        HttpServer server = referenceSite("/", "/about/", "/posts/hello/");
        try {
            run(new RecordingMigrator() {
                @Override
                protected void analyze(SourceSite source, SiteAnalysis analysis) throws MigrationException {
                    super.analyze(source, analysis);
                    analysis.referenceSite(url(server));
                }
            }, inPlace());
        } finally {
            server.stop(0);
        }

        assertThat(references).containsExactly(url(server) + " []");
    }

    @Test
    void validatesWithoutAReferenceSiteThatCannotBeReached() throws Exception {
        HttpServer server = referenceSite();
        server.stop(0);

        MigrationResult result = run(new MigrationOptions(source, true, recordingOutput, url(server)));

        assertThat(references).containsExactly("none");
        assertThat(result.warnings()).extracting(MigrationReport.Warning::stage).containsExactly("validate");
        assertThat(warnings).anyMatch(w -> w.startsWith("  Cannot reach " + url(server) + " (")
                && w.endsWith("), so the site was not compared with it"));
    }

    @Test
    void validatesWithoutAReferenceSiteWhenNotBuilt() throws Exception {
        run(new MigrationOptions(source, false, recordingOutput, URI.create("http://localhost:1")));

        assertThat(references).containsExactly("none");
        assertThat(warnings).isEmpty();
    }

    @Test
    void writesTheActionItemsOfTheValidation() throws Exception {
        run(new RecordingMigrator() {
            @Override
            protected void validate(TargetSite site, ReferenceSite reference) {
                site.report().todo("Check the page URLs against the old site");
            }
        }, inPlace());

        assertThat(Files.readString(source.resolve(MigrationReport.ACTION_ITEMS_FILE)))
                .contains("## validate\n\n- [ ] Check the page URLs against the old site\n");
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

    /**
     * Leaves two templates and a Ruby plugin for the user.
     */
    class MigratorWithActionItems extends RecordingMigrator {
        @Override
        protected void convertTemplates(TargetSite site) {
            site.report().todo("Convert templates/layouts/gallery.html, which uses a Liquid tag Qute has no equivalent for");
            site.report().todo("Check the date format in templates/partials/footer.html");
        }

        @Override
        protected void generateCode(TargetSite site) {
            site.report().todo("Migrate _plugins/reading_time.rb by hand");
        }
    }

    @Test
    void writesNoActionItemsFileWhenThereIsNothingToDo() throws Exception {
        MigrationResult result = run(inPlace());

        assertThat(result.actionItems()).isEmpty();
        assertThat(source.resolve(MigrationReport.ACTION_ITEMS_FILE)).doesNotExist();
        assertThat(warnings).isEmpty();
    }

    @Test
    void writesActionItemsToAChecklistGroupedByStage() throws Exception {
        MigrationResult result = run(new MigratorWithActionItems(), inPlace());

        assertThat(result.actionItems()).extracting(MigrationReport.ActionItem::stage)
                .containsExactly("templates", "templates", "code");
        assertThat(source.resolve(MigrationReport.ACTION_ITEMS_FILE)).hasContent("""
                # Finishing the migration to Roq

                The migration from Jekyll left these for you to do by hand.
                Tick them off as you go, and delete this file when you are done.

                ## templates

                - [ ] Convert templates/layouts/gallery.html, which uses a Liquid tag Qute has no equivalent for
                - [ ] Check the date format in templates/partials/footer.html

                ## code

                - [ ] Migrate _plugins/reading_time.rb by hand
                """);
    }

    @Test
    void listsActionItemsAtTheEnd() throws Exception {
        run(new MigratorWithActionItems(), inPlace());

        assertThat(output).contains("  To do: Migrate _plugins/reading_time.rb by hand");
        assertThat(warnings).containsExactly(
                "3 things need your attention:",
                "  [templates] Convert templates/layouts/gallery.html, which uses a Liquid tag Qute has no equivalent for",
                "  [templates] Check the date format in templates/partials/footer.html",
                "  [code] Migrate _plugins/reading_time.rb by hand");
        assertThat(output).last().isEqualTo("They are listed in "
                + source.toAbsolutePath().resolve(MigrationReport.ACTION_ITEMS_FILE) + ".");
        // The summary comes after the build and the comparison with the reference site
        assertThat(calls).last().isEqualTo("validate");
    }

    @Test
    void writesActionItemsEvenIfTheBuildFails() {
        SiteBuilder failingBuilder = site -> {
            throw new MigrationException("Build failed");
        };

        assertThatThrownBy(() -> new MigrationRunner(new MigratorWithActionItems(), source, scaffolder, failingBuilder,
                inPlace()).run()).hasMessage("Build failed");
        assertThat(source.resolve(MigrationReport.ACTION_ITEMS_FILE)).exists();
    }

    @Test
    void doesNotOverwriteAnExistingActionItemsFile() throws Exception {
        Files.writeString(source.resolve(MigrationReport.ACTION_ITEMS_FILE), "Mine\n");

        MigrationResult result = run(new MigratorWithActionItems(), inPlace());

        assertThat(source.resolve(MigrationReport.ACTION_ITEMS_FILE)).hasContent("Mine");
        assertThat(result.warnings()).extracting(MigrationReport.Warning::message)
                .containsExactly(MigrationReport.ACTION_ITEMS_FILE
                        + " already exists, so the action items were not written to it");
        assertThat(warnings).contains("3 things need your attention:");
        assertThat(output).noneMatch(line -> line.startsWith("They are listed in"));
    }

    @Test
    void writesActionItemsWithQuteAndLiquidSyntaxAsIs() throws Exception {
        List<String> messages = List.of(
                "Remove the {| and |} Roq added around templates/partials/code.html",
                "Check the nested {||escape||} and the {|||| opener in content/docs/qute.md",
                "Replace {% break %} in templates/layouts/list.html",
                "Check {#for post in site.collections.posts} {post.title} {/for} in templates/layouts/home.html");
        SiteMigrator migrator = new RecordingMigrator() {
            @Override
            protected void convertTemplates(TargetSite site) {
                messages.forEach(site.report()::todo);
            }
        };

        run(migrator, inPlace());

        assertThat(Files.readString(source.resolve(MigrationReport.ACTION_ITEMS_FILE)))
                .contains(messages.stream().map(message -> "- [ ] " + message + "\n").toList());
    }
}
