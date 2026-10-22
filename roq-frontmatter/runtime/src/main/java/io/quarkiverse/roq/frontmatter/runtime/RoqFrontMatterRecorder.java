package io.quarkiverse.roq.frontmatter.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.quarkiverse.roq.LargeRecords;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkiverse.roq.frontmatter.runtime.model.SerializedPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.frontmatter.runtime.model.Sources;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.runtime.annotations.Recorder;
import io.quarkus.vertx.http.runtime.VertxHttpBuildTimeConfig;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class RoqFrontMatterRecorder {

    private final VertxHttpBuildTimeConfig httpConfig;
    private final RoqSiteConfig config;

    public RoqFrontMatterRecorder(VertxHttpBuildTimeConfig httpConfig, RoqSiteConfig config) {
        this.httpConfig = httpConfig;
        this.config = config;
    }

    public Supplier<RoqCollections> createRoqCollections(
            Map<ConfiguredCollection, Supplier<List<DocumentPage>>> collectionSuppliers) {
        return () -> {
            final var c = new HashMap<String, RoqCollection>();
            for (Map.Entry<ConfiguredCollection, Supplier<List<DocumentPage>>> e : collectionSuppliers.entrySet()) {
                c.put(e.getKey().id(), new RoqCollection(e.getKey(), e.getValue().get()));
            }
            return new RoqCollections(Map.copyOf(c));
        };
    }

    public Supplier<List<NormalPage>> createPages(String resourcePath) {
        return new LazyValue<>(() -> {
            List<SerializedPage> serialized = LargeRecords.deserialize(resourcePath);
            return serialized.stream().map(s -> (NormalPage) s.toPage()).toList();
        })::get;
    }

    public Supplier<List<DocumentPage>> createDocuments(String resourcePath) {
        return new LazyValue<>(() -> {
            List<SerializedPage> serialized = LargeRecords.deserialize(resourcePath);
            return serialized.stream().map(s -> (DocumentPage) s.toPage()).toList();
        })::get;
    }

    public Supplier<Map<String, DocumentPage>> createDocumentsById(List<Supplier<List<DocumentPage>>> documents) {
        return new LazyValue<>(() -> {
            final Map<String, DocumentPage> byId = new HashMap<>();
            for (Supplier<List<DocumentPage>> supplier : documents) {
                for (DocumentPage document : supplier.get()) {
                    byId.put(document.id(), document);
                }
            }
            return byId;
        })::get;
    }

    public Supplier<List<DocumentPage>> createDerivedDocuments(Supplier<Map<String, DocumentPage>> documentsById,
            List<String> documentIds) {
        return () -> {
            final List<DocumentPage> docs = new ArrayList<>(documentIds.size());
            for (String id : documentIds) {
                docs.add(documentsById.get().get(id));
            }
            return docs;
        };
    }

    public Supplier<Site> createSite(Supplier<List<NormalPage>> pagesSupplier,
            Supplier<RoqCollections> roqCollectionsSupplier) {
        return () -> {
            final List<NormalPage> pages = pagesSupplier.get();
            final NormalPage indexPage = pages.stream().filter(p -> p.source().isSiteIndex()).findFirst().orElseThrow();
            return new Site(indexPage.url(), config.imagesPath(),
                    indexPage.data(), pages, roqCollectionsSupplier.get());
        };
    }

    public Supplier<Sources> createSources(String resourcePath) {
        return new LazyValue<>(() -> new Sources(LargeRecords.deserialize(resourcePath)))::get;
    }

    public Consumer<Route> initializeResolverRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.method(HttpMethod.HEAD);
            r.order(config.routeOrder() - 10);
        };
    }

    public Consumer<Route> initializeRenderRoute() {
        return r -> {
            r.method(HttpMethod.GET);
            r.method(HttpMethod.HEAD);
            r.order(config.routeOrder());
        };
    }

    public Handler<RoutingContext> pageResolver(String resourcePath) {
        return new RoqPageResolverHandler(LargeRecords.deserialize(resourcePath));
    }

    public Handler<RoutingContext> renderHandler() {
        return new RoqRouteHandler(httpConfig, config);
    }
}
