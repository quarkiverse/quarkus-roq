package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkus.runtime.LocalesBuildTimeConfig;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqFrontMatterRecorder {

    private final VertxHttpBuildTimeConfig httpConfig;
    private final RoqSiteConfig config;
    private final LocalesBuildTimeConfig locales;

    public RoqFrontMatterRecorder(VertxHttpBuildTimeConfig httpConfig, RoqSiteConfig config, LocalesBuildTimeConfig locales) {
        this.httpConfig = httpConfig;
        this.config = config;
        this.locales = locales;
    }

    public Supplier<RoqCollections> createRoqCollections(
            Map<ConfiguredCollection, List<Supplier<DocumentPage>>> collectionSuppliers) {
        return () -> {
            final var c = new HashMap<String, RoqCollection>();
            for (Map.Entry<ConfiguredCollection, List<Supplier<DocumentPage>>> e : collectionSuppliers.entrySet()) {
                List<DocumentPage> docs = new ArrayList<>();
                for (Supplier<DocumentPage> v : e.getValue()) {
                    docs.add(v.get());
                }
                c.put(e.getKey().id(), new RoqCollection(e.getKey(), docs));
            }
            return new RoqCollections(Map.copyOf(c));
        };
    }

    public Supplier<NormalPage> createPage(RoqUrl url, PageSource source, JsonObject data, Paginator paginator) {
        return () -> new NormalPage(url, source, data, paginator);
    }

    public Supplier<DocumentPage> createDocument(String collection, RoqUrl url, PageSource source, JsonObject data,
            boolean hidden) {
        return () -> new DocumentPage(collection, url, source, data, hidden);
    }

    public Supplier<Site> createSite(RootUrl rootUrl, Supplier<NormalPage> indexPage,
            List<Supplier<NormalPage>> normalPagesSuppliers,
            Supplier<RoqCollections> roqCollectionsSupplier) {
        return () -> {
            final List<NormalPage> pages = new ArrayList<>();
            for (Supplier<NormalPage> pagesSupplier : normalPagesSuppliers) {
                pages.add(pagesSupplier.get());
            }
            return new Site(indexPage.get().url(), config.imagesPath(),
                    indexPage.get().data(), pages, roqCollectionsSupplier.get());
        };
    }

    public Supplier<Sources> createSources(List<TemplateSource> list) {
        return () -> new Sources(list);
    }

    public Consumer<Route> initializeRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.order(config.routeOrder());
        };
    }

    public Handler<RoutingContext> handler(String rootPath,
            Map<String, Supplier<? extends Page>> pageSuppliers) {
        return new RoqRouteHandler(rootPath, httpConfig, pageSuppliers, config, locales);
    }

    public Handler<RoutingContext> aliasRoute(String target) {
        return ctx -> ctx.redirect(target);
    }

}
