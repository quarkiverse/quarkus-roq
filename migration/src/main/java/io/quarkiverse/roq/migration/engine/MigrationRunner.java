package io.quarkiverse.roq.migration.engine;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Stream;

/**
 * Runs a migration: the stages of a {@link SiteMigrator}, in their fixed order, with the caller's {@link Scaffolder}
 * and {@link SiteBuilder} in between.
 * <p>
 * The runner also owns the rules that keep the original site safe: the output directory is checked before anything
 * runs, and the site is copied to it only after the read-only stages have passed.
 */
public final class MigrationRunner {

    public record MigrationResult(Path siteDir, SiteAnalysis analysis, List<MigrationReport.Warning> warnings) {
    }

    @FunctionalInterface
    private interface Stage {
        void run() throws Exception;
    }

    private final SiteMigrator migrator;
    private final Path sourceDir;
    private final Path siteDir;
    private final Scaffolder scaffolder;
    private final SiteBuilder builder;
    private final boolean build;
    private final MigrationOutput output;

    public MigrationRunner(SiteMigrator migrator, Path sourceDir, Scaffolder scaffolder, SiteBuilder builder,
            MigrationOptions options) {
        this.migrator = migrator;
        this.sourceDir = sourceDir.toAbsolutePath().normalize();
        this.siteDir = options.outputDir().toAbsolutePath().normalize();
        this.scaffolder = scaffolder;
        this.builder = builder;
        this.build = options.build();
        this.output = options.output();
    }

    public boolean inPlace() {
        return sourceDir.equals(siteDir);
    }

    /**
     * @throws MigrationException if the output directory cannot be used or a stage fails
     */
    public MigrationResult run() throws MigrationException {
        validateDirectories();
        SiteAnalysis analysis = new SiteAnalysis();
        MigrationReport report = new MigrationReport(output);
        SourceSite source = new SourceSite(sourceDir);
        TargetSite site = new TargetSite(siteDir, inPlace(), analysis, report);

        stage(report, "preflight", "Check the site can be migrated", () -> migrator.preflight(source));
        stage(report, "analyze", "Inspect the site", () -> migrator.analyze(source, analysis));
        if (!inPlace()) {
            copySite();
        }
        stage(report, "scaffold", "Create the Roq project", () -> scaffolder.scaffold(site));
        stage(report, "restructure", "Move files into the Roq layout", () -> migrator.restructure(site));
        stage(report, "frontmatter", "Convert page front matter", () -> migrator.convertFrontMatter(site));
        stage(report, "config", "Convert site configuration", () -> migrator.convertConfig(site));
        stage(report, "templates", "Convert layouts and partials to Qute", () -> migrator.convertTemplates(site));
        stage(report, "content", "Fix links and redirects in content", () -> migrator.convertContent(site));
        stage(report, "assets", "Fix static assets and stylesheets", () -> migrator.convertAssets(site));
        stage(report, "code", "Generate Java code for site-specific extensions", () -> migrator.generateCode(site));
        stage(report, "check", "Check the converted site", () -> migrator.checkResult(site));
        if (build) {
            stage(report, "build", "Build the site", () -> builder.build(site));
        }
        return new MigrationResult(siteDir, analysis, report.warnings());
    }

    private void stage(MigrationReport report, String name, String description, Stage stage)
            throws MigrationException {
        output.info("");
        output.info("[" + name + "] " + description);
        report.currentStage(name);
        try {
            stage.run();
        } catch (MigrationException e) {
            throw e;
        } catch (Exception e) {
            throw new MigrationException("Migration stage '" + name + "' failed: " + e.getMessage(), e);
        } finally {
            report.currentStage(null);
        }
    }

    private void validateDirectories() throws MigrationException {
        if (!Files.isDirectory(sourceDir)) {
            throw new MigrationException("Not a directory: " + sourceDir);
        }
        if (inPlace()) {
            return;
        }
        if (siteDir.startsWith(sourceDir)) {
            throw new MigrationException("The output directory " + siteDir + " must not be inside the site being migrated");
        }
        if (sourceDir.startsWith(siteDir)) {
            throw new MigrationException("The output directory " + siteDir + " must not contain the site being migrated");
        }
        if (Files.exists(siteDir)) {
            if (!Files.isDirectory(siteDir)) {
                throw new MigrationException("The output path " + siteDir + " is a file");
            }
            try (Stream<Path> entries = Files.list(siteDir)) {
                if (entries.findAny().isPresent()) {
                    throw new MigrationException("The output directory " + siteDir + " is not empty");
                }
            } catch (IOException e) {
                throw new MigrationException("Cannot read the output directory " + siteDir + ": " + e.getMessage(), e);
            }
        }
    }

    private void copySite() throws MigrationException {
        output.info("");
        output.info("Copying " + sourceDir + " to " + siteDir);
        try {
            Files.createDirectories(siteDir);
            Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(sourceDir) && SourceSite.IGNORED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(siteDir.resolve(sourceDir.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.copy(file, siteDir.resolve(sourceDir.relativize(file)), LinkOption.NOFOLLOW_LINKS,
                            StandardCopyOption.COPY_ATTRIBUTES);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MigrationException("Cannot copy the site to " + siteDir + ": " + e.getMessage(), e);
        }
    }
}
