package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
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

    public Supplier<NormalPage> createPage(RoqUrl url, PageInfo info, JsonObject data, Paginator paginator) {
        return () -> new NormalPage(url, info, data, paginator);
    }

    public Supplier<DocumentPage> createDocument(String collection, RoqUrl url, PageInfo info, JsonObject data) {
        return () -> new DocumentPage(collection, url, info, data);
    }

    public Supplier<Site> createSite(RootUrl rootUrl, Supplier<NormalPage> indexPage,
            List<Supplier<NormalPage>> normalPagesSuppliers,
            Supplier<RoqCollections> roqCollectionsSupplier) {
        return () -> {
            final List<NormalPage> pages = new ArrayList<>();
            for (Supplier<NormalPage> pagesSupplier : normalPagesSuppliers) {
                pages.add(pagesSupplier.get());
            }
            return new Site(rootUrl, indexPage.get().url(), rootUrl.resolve(indexPage.get().info().imagesRootPath()),
                    indexPage.get().data(), pages, roqCollectionsSupplier.get());
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

    public Handler<RoutingContext> aliasRoute(String target) {
        return ctx -> ctx.redirect(target);
    }
}
