package io.quarkiverse.roq.frontmatter.runtime.config;

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
    String IGNORED_FILES = "**/_**,_**,.**";
    List<ConfiguredCollection> DEFAULT_COLLECTIONS = List
            .of(new ConfiguredCollection("posts", false, false, false, ":theme/post"));

    /**
     * The root path of your site (e.g. /blog) relative the quarkus http root path.
     * This is to be used only when the site is living next to a Quarkus application.
     * Use `quarkus.http.root-path` for GitHub Pages relative url.
     */
    @WithName("root-path")
    @WithDefault("/")
    String rootPath();

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
     * The ignored files in the site directory (you can use glob expressions).
     */
    @WithDefault(IGNORED_FILES)
    List<String> ignoredFiles();

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
     * Format for dates
     */
    @WithDefault("yyyy-MM-dd[ HH:mm][:ss][ Z]")
    String dateFormat();

    /**
     * The default timezone
     */
    @ConfigDocDefault("document timezone if provided or system timezone")
    Optional<String> timeZone();

    /**
     * The directory names (in the Roq site directory) containing collections as key
     * and the corresponding collection config as value
     */
    @ConfigDocDefault("posts={id: post, hidden: false}")
    @WithName("collections")
    Map<String, CollectionConfig> collectionsMap();

    default List<ConfiguredCollection> collections() {
        if (collectionsMap().isEmpty()) {
            return DEFAULT_COLLECTIONS;
        }
        return collectionsMap().entrySet().stream().filter(e -> e.getValue().enabled())
                .map(e -> new ConfiguredCollection(e.getKey(), false, e.getValue().hidden(), e.getValue().future(),
                        e.getValue().layout().orElse(null)))
                .toList();
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
