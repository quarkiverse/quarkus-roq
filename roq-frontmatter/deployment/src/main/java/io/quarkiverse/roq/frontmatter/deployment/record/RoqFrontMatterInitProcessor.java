package io.quarkiverse.roq.frontmatter.deployment.record;

import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterRecorder;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;

class RoqFrontMatterInitProcessor {

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindCollections(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishDerivedCollectionBuildItem> generatedCollections,
            BuildProducer<RoqFrontMatterCollectionBuildItem> collectionsProducer,
            BuildProducer<RoqFrontMatterPageBuildItem> pagesProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }

        // Published collections
        final Map<String, List<RoqFrontMatterPublishDocumentPageBuildItem>> byCollection = documents.stream()
                .collect(Collectors.groupingBy(RoqFrontMatterPublishDocumentPageBuildItem::collection));
        final Map<String, Supplier<DocumentPage>> documentsById = new HashMap<>();
        for (Map.Entry<String, List<RoqFrontMatterPublishDocumentPageBuildItem>> e : byCollection.entrySet()) {
            List<Supplier<DocumentPage>> docs = new ArrayList<>();
            for (RoqFrontMatterPublishDocumentPageBuildItem item : e.getValue()) {
                final RoqUrl url = item.url();
                final Supplier<DocumentPage> document = recorder.createDocument(item.collection(),
                        url,
                        item.info(), item.data());
                documentsById.put(item.info().id(), document);
                pagesProducer.produce(new RoqFrontMatterPageBuildItem(item.info().id(), url, document));
                docs.add(document);
            }
            collectionsProducer.produce(new RoqFrontMatterCollectionBuildItem(e.getKey(), docs));
        }

        // Derived collections (referencing existing documents)
        for (RoqFrontMatterPublishDerivedCollectionBuildItem i : generatedCollections) {
            List<Supplier<DocumentPage>> docs = new ArrayList<>();
            for (String id : i.documentIds()) {
                final Supplier<DocumentPage> doc = documentsById.get(id);
                if (doc == null) {
                    throw new IllegalStateException("No document found for id " + id);
                }
                docs.add(doc);
            }
            collectionsProducer.produce(new RoqFrontMatterCollectionBuildItem(i.collection(), docs));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindPages(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterPublishPageBuildItem> pages,
            BuildProducer<RoqFrontMatterNormalPageBuildItem> normalPagesProducer,
            BuildProducer<RoqFrontMatterPageBuildItem> pagesProducer,
            BuildProducer<RoqFrontMatterIndexPageBuildItem> indexPageProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }
        for (RoqFrontMatterPublishPageBuildItem page : pages) {
            final Supplier<NormalPage> recordedPage = recorder.createPage(page.url(),
                    page.info(), page.data(), page.paginator());
            pagesProducer.produce(new RoqFrontMatterPageBuildItem(page.info().id(), page.url(), recordedPage));
            normalPagesProducer.produce(new RoqFrontMatterNormalPageBuildItem(page.info().id(), page.url(), recordedPage));
            if (page.info().id().equals("index")) {
                indexPageProducer.produce(new RoqFrontMatterIndexPageBuildItem(recordedPage));
            }
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    RoqFrontMatterOutputBuildItem bindSite(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            RoqFrontMatterIndexPageBuildItem indexPageItem,
            List<RoqFrontMatterCollectionBuildItem> collectionItems,
            List<RoqFrontMatterPageBuildItem> pageItems,
            List<RoqFrontMatterNormalPageBuildItem> normalPageItems,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            RoqFrontMatterRecorder recorder) throws BuildException {
        if (rootUrlItem == null) {
            return null;
        }
        // Create Site bean
        if (indexPageItem == null) {
            throw new BuildException("Roq site must declare an index.html page");
        }
        final Map<String, List<Supplier<DocumentPage>>> collectionsMap = collectionItems.stream().collect(
                Collectors.toMap(RoqFrontMatterCollectionBuildItem::name, RoqFrontMatterCollectionBuildItem::documents));
        final Supplier<RoqCollections> collectionsSupplier = recorder.createRoqCollections(collectionsMap);
        final List<Supplier<NormalPage>> pages = normalPageItems.stream().map(RoqFrontMatterNormalPageBuildItem::page).toList();
        final Supplier<Site> siteSupplier = recorder.createSite(rootUrlItem.rootUrl(), indexPageItem.page(), pages,
                collectionsSupplier);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Site.class)
                .scope(Singleton.class)
                .unremovable()
                .supplier(siteSupplier)
                .done());

        final Map<String, Supplier<? extends Page>> allPagesByPath = pageItems.stream()
                .collect(Collectors.toMap(i -> i.url().path(), RoqFrontMatterPageBuildItem::page));
        return new RoqFrontMatterOutputBuildItem(allPagesByPath, collectionsSupplier);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    public RouteBuildItem produceRoute(RoqSiteConfig config, RoqFrontMatterRecorder recorder,
            HttpRootPathBuildItem httpRootPath, RoqFrontMatterOutputBuildItem roqFrontMatterOutput) {
        if (roqFrontMatterOutput == null || roqFrontMatterOutput.allPagesByPath().isEmpty()) {
            // There are no templates to serve
            return null;
        }
        return httpRootPath.routeBuilder()
                .routeFunction(httpRootPath.relativePath(removeTrailingSlash(config.rootPath()) + "/*"),
                        recorder.initializeRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.handler(httpRootPath.getRootPath(), roqFrontMatterOutput.allPagesByPath()))
                .build();
    }
}
