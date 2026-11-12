package io.quarkiverse.roq.migration.engine;

/**
 * Converts one type of site to Roq.
 * <p>
 * A migrator fills in one method per stage, and the {@link MigrationRunner} always calls them in the order they are
 * declared here, with project creation and the build in between:
 * <ol>
 * <li>{@link #preflight} and {@link #analyze} only get a read-only {@link SourceSite}, so they run before anything is
 * written;</li>
 * <li>the runner then creates the Roq project with its {@link Scaffolder};</li>
 * <li>{@link #restructure} through {@link #checkResult} convert the {@link TargetSite};</li>
 * <li>the runner then builds the site with its {@link SiteBuilder};</li>
 * <li>finally {@link #validate} compares the built site with the {@link ReferenceSite}, when there is one.</li>
 * </ol>
 * Every stage must be implemented; leave the body empty, with a comment, when a site type has nothing to do in it.
 * Report progress and non-fatal problems through {@link TargetSite#report()}, and throw {@link MigrationException} to
 * stop the migration with a message for the user.
 */
public abstract class SiteMigrator {

    public abstract SiteType type();

    /**
     * Refuse sites that cannot be migrated, e.g. because they are already converted.
     */
    protected abstract void preflight(SourceSite source) throws MigrationException;

    /**
     * Record what later stages need, such as {@link SiteAnalysis#projectName(String) the project name} and
     * {@link SiteAnalysis#extensions() the extensions} the {@link Scaffolder} adds.
     */
    protected abstract void analyze(SourceSite source, SiteAnalysis analysis) throws MigrationException;

    /**
     * Move files into the Roq layout: {@code content/}, {@code templates/}, {@code data/}, {@code public/},
     * {@code web/}.
     */
    protected abstract void restructure(TargetSite site) throws Exception;

    protected abstract void convertFrontMatter(TargetSite site) throws Exception;

    protected abstract void convertConfig(TargetSite site) throws Exception;

    protected abstract void convertTemplates(TargetSite site) throws Exception;

    /**
     * Fix links and redirects in content pages.
     */
    protected abstract void convertContent(TargetSite site) throws Exception;

    /**
     * Fix static assets and stylesheets.
     */
    protected abstract void convertAssets(TargetSite site) throws Exception;

    /**
     * Generate Java code for site-specific template extensions.
     */
    protected abstract void generateCode(TargetSite site) throws Exception;

    /**
     * Fail on anything the conversion left behind that would break the site.
     */
    protected abstract void checkResult(TargetSite site) throws Exception;

    /**
     * Compare the built site with the site before the migration, and report the differences; or, when there is
     * nothing to compare with, report what the user should check themselves.
     *
     * @param reference the site before the migration; null if the site was not built, there is no reference site, or
     *        it cannot be reached
     */
    protected abstract void validate(TargetSite site, ReferenceSite reference) throws Exception;
}
