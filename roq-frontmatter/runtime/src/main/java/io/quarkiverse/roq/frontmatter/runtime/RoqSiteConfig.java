package io.quarkiverse.roq.frontmatter.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.roq.site")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqSiteConfig {
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
}