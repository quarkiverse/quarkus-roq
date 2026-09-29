package io.quarkiverse.roq.plugin.sitemap.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "quarkus.roq.sitemap")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface RoqPluginSitemapConfig {

    /**
     * Where to read the last modification date of pages and documents from (used for {@code <lastmod>}), when not
     * set in the FM data with {@code last-modified-at}.
     * <p>
     * In dev mode, it defaults to {@code fs} to keep startup fast on large sites; set
     * {@code %dev.quarkus.roq.sitemap.last-modified=git} to override it.
     */
    @WithDefault("git")
    LastModifiedSource lastModified();

    enum LastModifiedSource {
        /**
         * The date of the last git commit touching the file (falls back to the file system date for uncommitted files)
         */
        GIT,
        /**
         * The file system last modification date of the file
         */
        FS,
        /**
         * Don't compute it (falls back to the page date)
         */
        NONE
    }

}
