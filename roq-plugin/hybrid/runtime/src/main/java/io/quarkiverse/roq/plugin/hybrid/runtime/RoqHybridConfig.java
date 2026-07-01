package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigDocDefault;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "site.hybrid")
@ConfigRoot(phase = ConfigPhase.BUILD_AND_RUN_TIME_FIXED)
public interface RoqHybridConfig {

    /**
     * Cache store backend.
     * <ul>
     * <li>{@code filesystem}: rendered pages are written to disk (survives restarts, low memory usage)</li>
     * <li>{@code memory}: rendered pages are kept in memory using Caffeine (faster, lost on restart)</li>
     * </ul>
     */
    @WithDefault("filesystem")
    CacheStore cacheStore();

    /**
     * Directory for filesystem cache storage.
     * Only used when {@code cache-store=filesystem}.
     */
    Optional<String> cacheDir();

    /**
     * Default cache mode for pages.
     * Can be overridden per-page via frontmatter {@code cached: lazy|startup|false}.
     */
    @WithDefault("lazy")
    CacheMode cacheMode();

    /**
     * Enable caching in dev mode. By default, caching is disabled in dev and test mode
     * so that template changes are always reflected immediately. Set to {@code true}
     * to test caching behavior during development.
     */
    @WithDefault("false")
    boolean cacheInDevMode();

    /**
     * Maximum number of pages to keep in the runtime cache.
     * When the cache exceeds this size, the least recently used entries are evicted.
     */
    @WithDefault("1000")
    int cacheMaxSize();

    /**
     * Time-to-live for cached pages. When set, cached entries expire after this duration
     * and will be re-rendered on the next request.
     * Example values: 5m, 1h, 30s.
     */
    Optional<Duration> cacheTtl();

    /**
     * Identifier used as a subdirectory in the filesystem cache to isolate builds.
     * When not set, defaults to the Quarkus application UUID (changes on each restart).
     * Set to a fixed value (e.g. git hash, app version) to persist the cache across restarts.
     */
    @ConfigDocDefault("${quarkus.uuid}")
    Optional<String> cacheBuildId();

    enum CacheStore {
        FILESYSTEM,
        MEMORY
    }

    enum CacheMode {
        FALSE("false"),
        STARTUP("startup"),
        LAZY("lazy");

        private final String value;

        CacheMode(String value) {
            this.value = value;
        }

        public static CacheMode fromString(String value) {
            return Arrays.stream(values()).filter(a -> a.value().equalsIgnoreCase(value)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Invalid cache mode '" + value + "'. Allowed values: false, startup, lazy."));
        }

        public String value() {
            return value;
        }
    }
}
