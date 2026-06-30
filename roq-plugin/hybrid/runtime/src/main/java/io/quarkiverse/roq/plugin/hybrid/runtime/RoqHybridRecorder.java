package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqHybridRecorder {

    private final RoqHybridConfig config;
    private final RoqSiteConfig siteConfig;
    private final VertxHttpBuildTimeConfig httpConfig;

    public RoqHybridRecorder(RoqHybridConfig config, RoqSiteConfig siteConfig, VertxHttpBuildTimeConfig httpConfig) {
        this.config = config;
        this.siteConfig = siteConfig;
        this.httpConfig = httpConfig;
    }

    public Consumer<Route> initializeHybridRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.order(siteConfig.routeOrder() - 5);
        };
    }

    public Handler<RoutingContext> hybridHandler() {
        RoqCacheManager cacheManager = io.quarkus.arc.Arc.container().instance(RoqCacheManager.class).get();
        List<String> compressMediaTypes = httpConfig.enableCompression()
                ? httpConfig.compressMediaTypes().orElse(List.of())
                : null;
        boolean cachingEnabled = !io.quarkus.runtime.LaunchMode.current().isDevOrTest() || config.cacheInDevMode();
        return new RoqHybridHandler(cacheManager, siteConfig, compressMediaTypes, cachingEnabled);
    }

    public void startupCache(Map<String, Supplier<? extends Page>> pages) {
        if (io.quarkus.runtime.LaunchMode.current().isDevOrTest() && !config.cacheInDevMode()) {
            return;
        }
        new RoqHybridStartup(pages, siteConfig).cacheStartupPages();
    }
}
