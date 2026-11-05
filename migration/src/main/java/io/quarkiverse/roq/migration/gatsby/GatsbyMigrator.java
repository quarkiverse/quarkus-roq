package io.quarkiverse.roq.migration.gatsby;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import io.quarkiverse.roq.migration.MvnpmDependencies;
import io.quarkiverse.roq.migration.SiteFiles;
import io.quarkiverse.roq.migration.engine.MigrationException;
import io.quarkiverse.roq.migration.engine.SiteAnalysis;
import io.quarkiverse.roq.migration.engine.SiteMigrator;
import io.quarkiverse.roq.migration.engine.SiteType;
import io.quarkiverse.roq.migration.engine.SourceSite;
import io.quarkiverse.roq.migration.engine.TargetSite;

/**
 * Migrates a Gatsby site that renders Markdown with {@code gatsby-transformer-remark}.
 * <p>
 * Gatsby layouts are React components that query their data with GraphQL, so they cannot be converted mechanically.
 * This migrator moves the content, configuration, styles and static files into the Roq layout, writes a minimal Qute
 * layout so the site builds and can be browsed, and reports every component and plugin as an action item.
 * <p>
 * The Gatsby project may be the migrated directory itself, or a subdirectory such as {@code site/} when its content
 * lives next to it (e.g. {@code path: `${__dirname}/../docs`}); in that case the whole repository is migrated and
 * becomes the Roq project.
 */
public class GatsbyMigrator extends SiteMigrator {

    static final List<String> PROJECT_FILES = List.of("package.json", "package-lock.json", "yarn.lock",
            "gatsby-config.js", "gatsby-config.mjs", "gatsby-config.ts", "gatsby-browser.js", "gatsby-node.js",
            "gatsby-ssr.js");

    /**
     * Set by {@link #analyze}: one migrator instance converts one site.
     */
    private GatsbyProject project;

    /**
     * Set by {@link #restructure}: the home page, relative to content/.
     */
    private String homePage;

    @Override
    public SiteType type() {
        return SiteType.GATSBY;
    }

    @Override
    protected void preflight(SourceSite source) throws MigrationException {
        if (GatsbyProject.findRoot(source) == null) {
            throw new MigrationException("No gatsby-config.js found in " + source.name() + " or in "
                    + String.join("/, ", GatsbyProject.PROJECT_DIRS.subList(1, GatsbyProject.PROJECT_DIRS.size())) + "/");
        }
        if (source.isFile("pom.xml") && source.contains("pom.xml", "quarkus-roq")) {
            throw new MigrationException("This site already has a Roq pom.xml; it looks converted already");
        }
    }

    @Override
    protected void analyze(SourceSite source, SiteAnalysis analysis) throws MigrationException {
        project = GatsbyProject.read(source);
        // Fonts and other npm packages gatsby-browser.js loads, which the web bundler resolves from mvnpm
        project.npmVersions().forEach((npm, version) -> {
            if (version != null) {
                analysis.dependencies().add(MvnpmDependencies.dependency(npm, version));
            }
        });
        String slug = slugify(project.config().siteMetadata().getOrDefault("title", source.name()));
        analysis.projectName((slug.isEmpty() ? slugify(source.name()) : slug) + "-website");
    }

    @Override
    protected void restructure(TargetSite site) throws Exception {
        homePage = GatsbyContent.move(project, site);
    }

    @Override
    protected void convertFrontMatter(TargetSite site) throws Exception {
        GatsbyFrontMatter.convert(project, site, homePage);
    }

    @Override
    protected void convertConfig(TargetSite site) throws Exception {
        Path config = site.dir().resolve("config/application.properties");
        Files.createDirectories(config.getParent());
        StringBuilder properties = new StringBuilder();
        if (Files.exists(config)) {
            properties.append(Files.readString(config));
        }
        String siteUrl = project.config().siteMetadata().get("siteUrl");
        if (siteUrl != null) {
            properties.append("site.url=").append(siteUrl).append("\n");
        }
        // Gatsby serves pages at /<slug>/; Roq's :path is the slugified file path, which matches
        properties.append("site.page-link=/:path/\n");
        // Markdown pages often contain code with braces; with this, only {=...} is a Qute expression
        properties.append("quarkus.qute.alt-expr-syntax=true\n");
        Files.writeString(config, properties);

        GatsbyPlugins.notes(project).forEach(site.report()::info);
        GatsbyPlugins.actionItems(project, site.dir()).forEach(site.report()::todo);
    }

    @Override
    protected void convertTemplates(TargetSite site) throws Exception {
        GatsbyTemplates.convert(project, site, homePage);
    }

    @Override
    protected void convertContent(TargetSite site) throws Exception {
        if (project.config().plugins().contains(GatsbyEmoji.PLUGIN)) {
            GatsbyEmoji.convert(site);
        }
        for (Path mdx : SiteFiles.files(site.dir().resolve("content"), p -> p.toString().endsWith(".mdx"))) {
            site.report().todo("Convert " + SiteFiles.relativePath(site.dir(), mdx) + ": Roq has no MDX support");
        }
    }

    @Override
    protected void convertAssets(TargetSite site) throws Exception {
        GatsbyStyles.convert(project, site);
    }

    @Override
    protected void generateCode(TargetSite site) throws Exception {
        Path gatsbyNode = site.dir().resolve(project.path("gatsby-node.js"));
        if (Files.exists(gatsbyNode) && Files.readString(gatsbyNode).contains("createPage(")) {
            site.report().todo("Reproduce the pages " + project.path("gatsby-node.js") + " creates "
                    + "(createPage); Roq creates pages from content/ and collections");
        }
    }

    @Override
    protected void checkResult(TargetSite site) throws Exception {
        Path dir = site.dir();
        Path root = dir.resolve(project.path(""));
        GatsbySiteTests.Kept kept = GatsbySiteTests.keep(project, site);
        long unitTests = SiteFiles.files(root, p -> GatsbySiteTests.TEST_FILE.matcher(p.getFileName().toString()).matches())
                .stream().filter(p -> !kept.tests().contains(dir.relativize(p).toString().replace('\\', '/'))).count();
        if (unitTests > 0) {
            site.report().todo("The " + unitTests + " Jest unit tests of the React components go with them; "
                    + "write Roq tests (@RoqAndRoll) for what they covered");
        }
        GatsbyWorkflows.check(project, site, kept.scripts());

        // The Gatsby project is not needed any more; the original is untouched unless migrating in place
        if (!project.root().isEmpty()) {
            SiteFiles.deleteTree(root);
            site.report().info("Removed the Gatsby project in " + project.root() + "/");
        } else {
            for (String file : PROJECT_FILES) {
                Files.deleteIfExists(dir.resolve(file));
            }
            SiteFiles.deleteTree(dir.resolve("src"));
            SiteFiles.deleteTree(dir.resolve("plugins"));
            site.report().info("Removed the Gatsby project files");
        }
    }

    static String slugify(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
