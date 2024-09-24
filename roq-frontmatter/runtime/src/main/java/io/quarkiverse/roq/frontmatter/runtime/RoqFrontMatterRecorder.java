package io.quarkiverse.roq.frontmatter.runtime;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.RoqCollection.Paginator;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqFrontMatterRecorder {

    private final HttpBuildTimeConfig httpConfig;
    private final RoqSiteConfig config;

    public RoqFrontMatterRecorder(HttpBuildTimeConfig httpConfig, RoqSiteConfig config) {
        this.httpConfig = httpConfig;
        this.config = config;
    }

    public RuntimeValue<PageUrl> createSiteUrl() {
        return new RuntimeValue<>();
    }

    public Supplier<RoqCollections> createRoqCollections(Map<String, List<Supplier<Page>>> collectionSuppliers) {
        return () -> {
            final var c = new HashMap<String, RoqCollection>();
            for (Map.Entry<String, List<Supplier<Page>>> e : collectionSuppliers.entrySet()) {
                List<Page> pages = new ArrayList<>();
                for (Supplier<Page> v : e.getValue()) {
                    pages.add(v.get());
                }
                c.put(e.getKey(), new RoqCollection(pages));
            }
            return new RoqCollections(Map.copyOf(c));
        };
    }

    public Supplier<Page> createPage(RootUrl rootUrl, String id, JsonObject data, Paginator paginator) {
        return () -> new Page(rootUrl, id, data, paginator);
    }

    public Consumer<Route> initializeRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.order(config.routeOrder());
        };
    }

    public Handler<RoutingContext> handler(String rootPath, Supplier<RoqCollections> roqCollectionsSupplier,
            Map<String, Supplier<Page>> pageSuppliers) {
        return new RoqRouteHandler(rootPath, httpConfig, roqCollectionsSupplier, pageSuppliers);
    }

    public RuntimeValue<RootUrl> createRootUrl(RootUrl rootUrl) {
        return new RuntimeValue<>(rootUrl);
    }
}