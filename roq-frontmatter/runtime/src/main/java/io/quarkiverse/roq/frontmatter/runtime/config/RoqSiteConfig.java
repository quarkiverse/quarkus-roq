package io.quarkiverse.roq.frontmatter.runtime.config;

import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.constraints.Pattern;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

@ConfigMapping(prefix = "site")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqSiteConfig {
    String DIR_NAME_PATTERN = "^[a-zA-Z0-9._-]{1,255}$";
    String CONTENT_DIR = "content";
    String STATIC_DIR = "static";
    String PUBLIC_DIR = "public";
    String IGNORED_FILES = "**.DS_Store,**Thumbs.db,**/_**,_**";

    List<ConfiguredCollection> DEFAULT_COLLECTIONS = List
            .of(new ConfiguredCollection("posts", false, false, false, ":theme/post"));

    /**
     * the base hostname & protocol for your site, e.g. http://example.com
     */
    @WithName("url")
    Optional<String> urlOptional();

    /**
     * The order of the route which handles the templates.
     * <p>
     * By default, the route is executed before the default routes (static resources, etc.).
     *
     * @asciidoclet
     */
    @WithDefault("1100")
    int routeOrder();

    /**
     * Add new ignored files to the default list.
     *
     * The ignored files (relative to the site directory).
     *
     * <p>
     * Only the <code>content/</code>, <code>public/</code>, and <code>static/</code> directories are scanned.
     * </p>
     */
    Optional<List<String>> ignoredFiles();

    /**
     * The default ignored files (relative to the site directory) include:
     * <ul>
     * <li><code>.DS_Store</code></li>
     * <li><code>Thumbs.db</code></li>
     * <li>All files or directories starting with an underscore (<code>_</code>)</li>
     * </ul>
     *
     * <p>
     * Only the <code>content/</code>, <code>public/</code>, and <code>static/</code> directories are scanned.
     * </p>
     */
    @WithDefault(IGNORED_FILES)
    List<String> defaultIgnoredFiles();

    /**
     * Pages whose content should be escaped&mdash;
     * i.e., included in Qute rendering but not parsed for Qute expressions.
     *
     * <p>
     * This is based on the page's relative path from the content directory.
     * </p>
     *
     * <p>
     * This applies only to <em>pages</em> (not layouts or partials).
     * </p>
     *
     * <p>
     * Supports glob expressions.
     * </p>
     */
    Optional<List<String>> escapedPages();

    /**
     * The layout to use for normal html pages if not specified in FM.
     * When empty, the page will not use a layout when it doesn't specify it in FM.
     *
     * ":theme/" is removed if no theme is defined.
     */
    @WithDefault(":theme/page")
    Optional<String> pageLayout();

    /**
     * The directory which contains content (pages and collections) in the Roq site directory.
     */
    @WithDefault(CONTENT_DIR)
    @Pattern(regexp = DIR_NAME_PATTERN)
    String contentDir();

    /**
     * The directory (dir name) which contains static files to be served (with 'static/' prefix).
     *
     * @deprecated Use publicDir instead (Use 'public/static/...' to reproduce the same behaviour)
     */
    @WithDefault(STATIC_DIR)
    @Pattern(regexp = DIR_NAME_PATTERN)
    String staticDir();

    /**
     * The directory which contains public static files to be served without processing (dir name)
     */
    @WithDefault(PUBLIC_DIR)
    @Pattern(regexp = DIR_NAME_PATTERN)
    String publicDir();

    /**
     * The path containing static images (in the public directory)
     */
    @WithDefault("images/")
    String imagesPath();

    /**
     * When enabled it will select all FrontMatter pages in Roq Generator
     */
    @WithDefault("true")
    boolean generator();

    /**
     * Show future documents
     */
    @WithDefault("false")
    boolean future();

    /**
     * This will be used to replace `:theme` when resolving layouts (e.g. `layout: :theme/main.html`)
     */
    Optional<String> theme();

    /**
     * Show draft pages
     */
    @WithDefault("false")
    boolean draft();

    /**
     * Directory under which all documents will be considered as drafts.
     */
    @WithDefault("drafts")
    String draftDirectory();

    /**
     * Format for dates
     */
    @WithDefault("yyyy-MM-dd[ HH:mm][:ss][ Z]")
    String dateFormat();

    /**
     * The default timezone
     */
    @ConfigDocDefault("document timezone if provided or system timezone")
    Optional<String> timeZone();

    default ZoneId timeZoneOrDefault() {
        return timeZone().isPresent() ? ZoneId.of(timeZone().get()) : ZoneId.systemDefault();
    }

    /**
     * The default language to use when no language is specified in the frontmatter.
     * This language will be used as a fallback for articles that don't have a 'locale' property.
     */
    @WithDefault("en")
    String defaultLocale();

    /**
     * Indicates whether file names in the public directory and files attached to pages should be slugified
     * (converted to a URL-friendly format).
     *
     * When enabled, file names will automatically be transformed into a URL-safe format.
     * Additionally, `page.file` and `site.file` references can use the original file names,
     * as they will also be slugified during the process.
     */
    @WithDefault("true")
    boolean slugifyFiles();

    /**
     * The directory names (in the Roq site directory) containing collections as key
     * and the corresponding collection config as value
     */
    @ConfigDocDefault("posts=true")
    @WithName("collections")
    Map<String, CollectionConfig> collectionsMap();

    /**
     * The directory where the generated templates should be created inside the output directory.
     */
    @WithDefault("roq-templates")
    @Pattern(regexp = DIR_NAME_PATTERN)
    String generatedTemplatesOutputDir();

    default List<ConfiguredCollection> collections() {
        if (collectionsMap().isEmpty()) {
            return DEFAULT_COLLECTIONS;
        }
        return collectionsMap().entrySet().stream().filter(e -> e.getValue().enabled())
                .map(e -> new ConfiguredCollection(e.getKey(), false, e.getValue().hidden(), e.getValue().future(),
                        e.getValue().layout().orElse(null)))
                .toList();
    }

    /**
     * <strong>READ CAREFULLY:</strong><br>
     * The root path of your site (e.g. <code>/blog</code>) should be set using
     * <code>quarkus.http.root-path</code>.<br>
     * This path prefix should be relative to the Quarkus HTTP root path and is meant to be used
     * only when the Roq site is served alongside a Quarkus application on a separate path.
     */
    Optional<String> pathPrefix();

    default String pathPrefixOrEmpty() {
        return pathPrefix().orElse("");
    }

    /**
     * Controls when templates are cached in hybrid mode.
     * <ul>
     * <li><code>false</code>: Templates are rendered at runtime (no caching)</li>
     * <li><code>startup</code>: Templates are cached at application startup</li>
     * <li><code>on-demand</code>: Templates are cached on first request</li>
     * </ul>
     * <p>
     * This allows mixing static generation with runtime rendering for hybrid applications.
     * </p>
     */
    @WithDefault("false")
    RuntimeCacheMode runtimeCache();

    enum RuntimeCacheMode {
        /**
         * Templates are rendered at runtime on every request
         */
        FALSE("false"),
        /**
         * Templates are cached at application startup
         */
        STARTUP("startup"),
        /**
         * Templates are cached on first request (lazy caching)
         */
        ON_DEMAND("on-demand");

        private final String value;

        RuntimeCacheMode(String value) {
            this.value = value;
        }

        public static RuntimeCacheMode fromString(String value) {
            return Arrays.stream(values()).filter(a -> a.value().equalsIgnoreCase(value)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No enum constant io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig.RuntimeCacheMode."
                                    + value));
        }

        public String value() {
            return value;
        }
    }

    interface CollectionConfig {
        /**
         * If this collection is enabled
         */
        @WithParentName
        @WithDefault("true")
        boolean enabled();

        /**
         * Show future documents (overrides global future for this collection)
         */
        @WithDefault("false")
        boolean future();

        /**
         * If true, the collection won't be available on path but consumable as data.
         */
        @WithDefault("false")
        boolean hidden();

        /**
         * The layout to use if not specified in FM data.
         * When empty, the document will not use a layout when it doesn't specify it in FM.
         *
         * ":theme/" is removed if no theme defined.
         */
        Optional<String> layout();
    }
}
