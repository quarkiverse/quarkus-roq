package io.quarkiverse.roq.plugin.hybrid.runtime;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqHybridRecorder {

    private final RoqHybridConfig config;
    private final RoqSiteConfig siteConfig;

    public RoqHybridRecorder(RoqHybridConfig config, RoqSiteConfig siteConfig) {
        this.config = config;
        this.siteConfig = siteConfig;
    }

    public Consumer<Route> initializeFilterRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.order(siteConfig.routeOrder() - 5);
        };
    }

    public Handler<RoutingContext> hybridFilter() {
        RoqCacheManager cacheManager = io.quarkus.arc.Arc.container().instance(RoqCacheManager.class).get();
        return new RoqHybridFilter(cacheManager, siteConfig);
    }

    public void startupCache(Map<String, Supplier<? extends Page>> pages) {
        if (io.quarkus.runtime.LaunchMode.current().isDevOrTest() && !config.cacheInDevMode()) {
            return;
        }
        new RoqHybridStartup(pages, siteConfig).cacheStartupPages();
    }
}
