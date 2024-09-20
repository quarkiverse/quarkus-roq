package io.quarkiverse.roq.frontmatter.runtime;

import static io.quarkiverse.roq.util.PathUtils.join;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.util.Optional;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "quarkus.roq.site")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqSiteConfig {
    /**
     * The root path of your site (e.g. /blog) relative the the quarkus http root path.
     */
    @WithName("root-path")
    @WithDefault("/")
    String rootPath();

    /**
     * the base hostname & protocol for your site, e.g. http://example.com
     */
    @WithName("url")
    Optional<String> urlOptional();

    default RootUrl url() {
        return new RootUrl(this.urlOptional().orElse(""), this.globalRootPath());
    }

    default String globalRootPath() {
        Config allConfig = ConfigProvider.getConfig();
        return join(allConfig.getValue("quarkus.http.root-path", String.class), removeTrailingSlash(rootPath()));
    }

    /**
     * The order of the route which handles the templates.
     *
     * By default, the route is executed before the default routes (static resources, etc.).
     *
     * @asciidoclet
     */
    @WithDefault("1100")
    int routeOrder();
}
