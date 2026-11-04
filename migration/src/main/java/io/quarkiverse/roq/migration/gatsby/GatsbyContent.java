package io.quarkiverse.roq.migration.gatsby;

import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.MigrationReport;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Moves a Gatsby site's Markdown pages, images and static files into the Roq layout.
 */
final class GatsbyContent {

    private GatsbyContent() {
    }

    /**
     * @return the home page, relative to {@code content/}, or null if the site has none
     */
    static String move(GatsbyProject project, TargetSite site) throws Exception {
        Path dir = site.dir();
        MigrationReport report = site.report();

        // Gatsby's build output and cache; public/ is also where Roq's static files go
        SiteFiles.deleteTree(dir.resolve(project.path("public")));
        SiteFiles.deleteTree(dir.resolve(project.path(".cache")));

        for (GatsbyConfig.Source s : project.config().sources()) {
            if (GatsbyProject.normalise(project.path(s.path())) == null) {
                report.todo("The Gatsby source '" + s.name() + "' (" + s.path() + ") is outside the migrated directory "
                        + "and was not copied; migrate a parent directory, or copy it into content/");
            }
        }

        String homePage = null;
        Path content = dir.resolve("content");
        boolean oneSource = project.markdownSources().size() == 1;
        for (String sourceDir : project.markdownSources()) {
            Path from = dir.resolve(sourceDir);
            Path to = oneSource ? content : content.resolve(Path.of(sourceDir).getFileName().toString());
            for (Path file : SiteFiles.files(from, p -> GatsbyProject.MARKDOWN.matcher(p.toString()).matches())) {
                // Moved as they are: Gatsby sites often share these pages with something else, such as a GitHub wiki
                Path destination = to.resolve(from.relativize(file).toString());
                if (project.homePage() != null && dir.resolve(project.homePage()).equals(file)) {
                    homePage = content.relativize(destination).toString().replace('\\', '/');
                }
                Files.createDirectories(destination.getParent());
                Files.move(file, destination);
            }
            report.info("Moved the Markdown pages in " + sourceDir + "/ to " + SiteFiles.relativePath(dir, to) + "/");
            if (SiteFiles.files(from, p -> true).isEmpty()) {
                SiteFiles.deleteTree(from);
            } else {
                report.todo(sourceDir + "/ still has files that are not Markdown; move the ones the site needs");
            }
        }
        if (homePage == null) {
            report.todo("Create content/index.html or content/index.md for the home page (src/pages/index.* in Gatsby)");
        }

        if (project.imageSource() != null) {
            SiteFiles.copyTree(dir.resolve(project.imageSource()), dir.resolve("public/images"));
            report.info("Copied " + project.imageSource() + "/ to public/images/");
        }
        // Gatsby serves the static/ folder at the site root, like Roq's public/
        SiteFiles.copyTree(dir.resolve(project.path("static")), dir.resolve("public"));
        return homePage;
    }
}
