package io.quarkiverse.roq.frontmatter.runtime;

import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "roq")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqSiteConfig {
    /**
     * the root path of your site, e.g. /blog
     */
    @WithName("root-path")
    @WithDefault("${quarkus.http.root-path}")
    String rootPath();

    /**
     * the base hostname & protocol for your site, e.g. http://example.com
     */
    @WithName("url")
    Optional<String> urlOptional();

    default RootUrl url() {
        return new RootUrl(this.urlOptional().orElse(""), this.rootPath());
    }

}
