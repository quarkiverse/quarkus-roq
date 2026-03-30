package io.quarkiverse.roq.frontmatter.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteIndexNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawLayoutBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.assemble.RoqFrontMatterRawPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterRecordedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterRecordedNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterRecordedPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterRecordedSiteIndexBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterRecorder;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;

// Records runtime objects via the Quarkus recorder pattern.
// All @Record methods here run at STATIC_INIT (build time) to create Supplier-based
// synthetic beans that will be instantiated at runtime.
class RoqFrontMatterStep5RecordProcessor {
    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep5RecordProcessor.class);

    // Register the Sources bean (lookup table for all template sources, used by the Qute engine)
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindSources(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterRawLayoutBuildItem> rawLayouts,
            List<RoqFrontMatterRawPageBuildItem> rawPages,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }
        final List<TemplateSource> list = new ArrayList<>();
        rawLayouts.stream().map(RoqFrontMatterRawLayoutBuildItem::templateSource).forEach(list::add);
        rawPages.stream().map(RoqFrontMatterRawPageBuildItem::templateSource).forEach(list::add);
        Supplier<Sources> sources = recorder.createSources(list);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Sources.class)
                .named("sources")
                .scope(Singleton.class)
                .unremovable()
                .supplier(sources)
                .done());
    }

    // Record document pages grouped by collection, and derived collections (e.g. tag pages).
    // Derived collections reference existing document suppliers by id — they don't create new pages.
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindCollections(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishDerivedCollectionBuildItem> generatedCollections,
            BuildProducer<RoqFrontMatterRecordedCollectionBuildItem> collectionsProducer,
            BuildProducer<RoqFrontMatterRecordedPageBuildItem> pagesProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }

        // Published collections
        final Map<ConfiguredCollection, List<RoqFrontMatterPublishDocumentPageBuildItem>> byCollection = documents.stream()
                .collect(Collectors.groupingBy(RoqFrontMatterPublishDocumentPageBuildItem::collection));
        final Map<String, Supplier<DocumentPage>> documentsById = new HashMap<>();
        for (Map.Entry<ConfiguredCollection, List<RoqFrontMatterPublishDocumentPageBuildItem>> e : byCollection.entrySet()) {
            List<Supplier<DocumentPage>> docs = new ArrayList<>();
            for (RoqFrontMatterPublishDocumentPageBuildItem item : e.getValue()) {
                final RoqUrl url = item.url();
                final Supplier<DocumentPage> document = recorder.createDocument(item.collection().id(),
                        url,
                        item.source(), item.data(), item.collection().hidden());
                documentsById.put(item.source().id(), document);
                pagesProducer
                        .produce(
                                new RoqFrontMatterRecordedPageBuildItem(item.source().id(), url, item.collection().hidden(),
                                        document));
                docs.add(document);
            }
            collectionsProducer.produce(new RoqFrontMatterRecordedCollectionBuildItem(e.getKey(), docs));
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
            collectionsProducer.produce(new RoqFrontMatterRecordedCollectionBuildItem(i.collection(), docs));
        }
    }

    // Record normal pages (non-collection pages) and identify the site index page
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindPages(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterPublishNormalPageBuildItem> pages,
            BuildProducer<RoqFrontMatterRecordedNormalPageBuildItem> normalPagesProducer,
            BuildProducer<RoqFrontMatterRecordedPageBuildItem> pagesProducer,
            BuildProducer<RoqFrontMatterRecordedSiteIndexBuildItem> indexPageProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }
        for (RoqFrontMatterPublishNormalPageBuildItem page : pages) {
            final Supplier<NormalPage> recordedPage = recorder.createPage(page.url(),
                    page.source(), page.data(), page.paginator());
            pagesProducer.produce(new RoqFrontMatterRecordedPageBuildItem(page.source().id(), page.url(), false, recordedPage));
            normalPagesProducer
                    .produce(new RoqFrontMatterRecordedNormalPageBuildItem(page.source().id(), page.url(), recordedPage));
            if (page.source().isSiteIndex()) {
                indexPageProducer.produce(new RoqFrontMatterRecordedSiteIndexBuildItem(recordedPage));
            }
        }
    }

    // Assemble the Site bean from all recorded pages/collections, detect path conflicts,
    // and return the output build item that downstream steps (Step6) depend on
    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    RoqFrontMatterOutputBuildItem bindSite(
            LaunchModeBuildItem launchMode,
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            RoqFrontMatterRecordedSiteIndexBuildItem indexPageItem,
            List<RoqFrontMatterRecordedCollectionBuildItem> collectionItems,
            List<RoqFrontMatterRecordedPageBuildItem> pageItems,
            List<RoqFrontMatterRecordedNormalPageBuildItem> normalPageItems,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return null;
        }
        // Create Site bean
        if (indexPageItem == null) {
            throw new RoqSiteIndexNotFoundException(
                    "Site index page (index.html, index.md, etc.) not found. A site index is required by Roq. Please create one to continue.");

        }
        final Map<ConfiguredCollection, List<Supplier<DocumentPage>>> collectionsMap = collectionItems.stream().collect(
                Collectors.toMap(RoqFrontMatterRecordedCollectionBuildItem::collection,
                        RoqFrontMatterRecordedCollectionBuildItem::documents));
        final Supplier<RoqCollections> collectionsSupplier = recorder.createRoqCollections(collectionsMap);
        final List<Supplier<NormalPage>> pages = normalPageItems.stream()
                .map(RoqFrontMatterRecordedNormalPageBuildItem::page)
                .toList();
        final Supplier<Site> siteSupplier = recorder.createSite(rootUrlItem.rootUrl(), indexPageItem.page(), pages,
                collectionsSupplier);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Site.class)
                .named("site")
                .scope(Singleton.class)
                .unremovable()
                .supplier(siteSupplier)
                .done());
        // Build the path → page map for the route handler, checking for duplicate paths.
        // Hidden pages (e.g. from hidden collections) are excluded from routing.
        Map<String, RoqFrontMatterRecordedPageBuildItem> itemsMap = new HashMap<>();
        Map<String, Supplier<? extends Page>> allPagesByPath = new HashMap<>();
        for (RoqFrontMatterRecordedPageBuildItem i : pageItems) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Published %spage '%s' on '%s'", i.hidden() ? "hidden " : "", i.id(), i.url().toString());
            }
            if (i.hidden()) {
                continue;
            }
            final RoqFrontMatterRecordedPageBuildItem prev = itemsMap.putIfAbsent(i.url().resourcePath(), i);
            if (prev != null) {
                if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                    LOGGER.warnf(
                            "Conflict detected: Duplicate path (%s) found in %s and %s. In development, the first occurrence will be kept, but this will cause an exception in normal mode.",
                            i.url().resourcePath(), prev.id(), i.id());
                    continue;
                } else {
                    throw new RoqPathConflictException(
                            "Conflict detected: Duplicate path (%s) found in %s and %s".formatted(i.url().resourcePath(),
                                    prev.id(),
                                    i.id()));
                }
            }
            allPagesByPath.put(i.url().resourcePath(), i.page());
        }
        return new RoqFrontMatterOutputBuildItem(allPagesByPath);
    }

    // Register the Vert.x route handler that serves rendered pages at runtime.
    // Runs at RUNTIME_INIT (after synthetic beans are available) so the handler
    // can look up Page suppliers from the allPagesByPath map.
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
                .routeFunction(httpRootPath.relativePath(StringPaths.join(config.pathPrefixOrEmpty(), "/*")),
                        recorder.initializeRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.handler(httpRootPath.getRootPath(), roqFrontMatterOutput.allPagesByPath()))
                .build();
    }
}
