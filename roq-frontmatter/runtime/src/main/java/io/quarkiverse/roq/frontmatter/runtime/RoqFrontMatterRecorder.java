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

    public Supplier<RoqCollections> createRoqCollections(Map<String, List<Supplier<DocumentPage>>> collectionSuppliers) {
        return () -> {
            final var c = new HashMap<String, RoqCollection>();
            for (Map.Entry<String, List<Supplier<DocumentPage>>> e : collectionSuppliers.entrySet()) {
                List<DocumentPage> docs = new ArrayList<>();
                for (Supplier<DocumentPage> v : e.getValue()) {
                    docs.add(v.get());
                }
                c.put(e.getKey(), new RoqCollection(docs));
            }
            return new RoqCollections(Map.copyOf(c));
        };
    }

    public Supplier<NormalPage> createPage(RootUrl rootUrl, String id, JsonObject data, Paginator paginator) {
        return new Supplier<NormalPage>() {
            @Override
            public NormalPage get() {
                return new NormalPage(rootUrl, id, data, paginator);
            }
        };
    }

    public Supplier<DocumentPage> createDocument(RootUrl rootUrl, String id, JsonObject data) {
        return new Supplier<DocumentPage>() {
            @Override
            public DocumentPage get() {
                return new DocumentPage(rootUrl, id, data);
            }
        };
    }

    public Supplier<Site> createSite(Supplier<NormalPage> site, List<Supplier<NormalPage>> pagesSuppliers,
            Supplier<RoqCollections> roqCollectionsSupplier) {
        return new Supplier<Site>() {
            @Override
            public Site get() {
                final List<NormalPage> pages = new ArrayList<>();
                for (Supplier<NormalPage> pagesSupplier : pagesSuppliers) {
                    pages.add(pagesSupplier.get());
                }
                return new Site(site.get(), pages, roqCollectionsSupplier.get());
            }
        };
    }

    public Consumer<Route> initializeRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.order(config.routeOrder());
        };
    }

    public Handler<RoutingContext> handler(String rootPath,
            Map<String, Supplier<? extends Page>> pageSuppliers) {
        return new RoqRouteHandler(rootPath, httpConfig, pageSuppliers);
    }

}