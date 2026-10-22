package io.quarkiverse.roq.frontmatter.deployment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.LargeRecords;
import io.quarkiverse.roq.deployment.items.LargeRecordBuildItem;
import io.quarkiverse.roq.exception.RoqException;
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
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterRecordedNormalPagesBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.record.RoqFrontMatterRecordedPageBuildItem;
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

class RoqFrontMatterStep5RecordProcessor {
    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep5RecordProcessor.class);
    private static final String RESOURCE_PREFIX = "roq-recorded/";

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindSources(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterRawLayoutBuildItem> rawLayouts,
            List<RoqFrontMatterRawPageBuildItem> rawPages,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            BuildProducer<LargeRecordBuildItem> resourceProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }
        final List<TemplateSource> list = new ArrayList<>();
        rawLayouts.stream().map(RoqFrontMatterRawLayoutBuildItem::templateSource).forEach(list::add);
        rawPages.stream().map(RoqFrontMatterRawPageBuildItem::templateSource).forEach(list::add);
        String resourcePath = RESOURCE_PREFIX + "sources.ser";
        resourceProducer.produce(new LargeRecordBuildItem(resourcePath, LargeRecords.serialize(list),
                TemplateSource.class, SourceFile.class));
        Supplier<Sources> sources = recorder.createSources(resourcePath);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Sources.class)
                .named("sources")
                .scope(Singleton.class)
                .unremovable()
                .supplier(sources)
                .done());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindCollections(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishDerivedCollectionBuildItem> generatedCollections,
            BuildProducer<RoqFrontMatterRecordedCollectionBuildItem> collectionsProducer,
            BuildProducer<RoqFrontMatterRecordedPageBuildItem> pagesProducer,
            BuildProducer<LargeRecordBuildItem> resourceProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }

        final Map<ConfiguredCollection, List<RoqFrontMatterPublishDocumentPageBuildItem>> byCollection = documents.stream()
                .collect(Collectors.groupingBy(RoqFrontMatterPublishDocumentPageBuildItem::collection));
        final Set<String> documentIds = new HashSet<>();
        final List<Supplier<List<DocumentPage>>> publishedDocuments = new ArrayList<>();
        for (Map.Entry<ConfiguredCollection, List<RoqFrontMatterPublishDocumentPageBuildItem>> e : byCollection.entrySet()) {
            final ConfiguredCollection collection = e.getKey();
            final List<SerializedPage> serialized = new ArrayList<>();
            for (RoqFrontMatterPublishDocumentPageBuildItem item : e.getValue()) {
                serialized.add(SerializedPage.ofDocument(collection.id(), item.url(), item.source(), item.data(),
                        collection.hidden()));
                documentIds.add(item.source().id());
                pagesProducer.produce(new RoqFrontMatterRecordedPageBuildItem(item.source().id(), item.url(),
                        collection.hidden()));
            }
            String resourcePath = RESOURCE_PREFIX + "documents-" + collection.id() + ".ser";
            resourceProducer.produce(new LargeRecordBuildItem(resourcePath, LargeRecords.serialize(serialized),
                    SerializedPage.SERIALIZABLE_CLASSES));
            final Supplier<List<DocumentPage>> docs = recorder.createDocuments(resourcePath);
            publishedDocuments.add(docs);
            collectionsProducer.produce(new RoqFrontMatterRecordedCollectionBuildItem(collection, docs));
        }

        if (generatedCollections.isEmpty()) {
            return;
        }
        final Supplier<Map<String, DocumentPage>> documentsById = recorder.createDocumentsById(publishedDocuments);
        for (RoqFrontMatterPublishDerivedCollectionBuildItem i : generatedCollections) {
            for (String id : i.documentIds()) {
                if (!documentIds.contains(id)) {
                    throw new IllegalStateException("No document found for id " + id);
                }
            }
            final Supplier<List<DocumentPage>> docs = recorder.createDerivedDocuments(documentsById,
                    new ArrayList<>(i.documentIds()));
            collectionsProducer.produce(new RoqFrontMatterRecordedCollectionBuildItem(i.collection(), docs));
        }
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindPages(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterPublishNormalPageBuildItem> pages,
            BuildProducer<RoqFrontMatterRecordedNormalPagesBuildItem> normalPagesProducer,
            BuildProducer<RoqFrontMatterRecordedPageBuildItem> pagesProducer,
            BuildProducer<LargeRecordBuildItem> resourceProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }
        List<RoqFrontMatterPublishNormalPageBuildItem> siteIndexPages = new ArrayList<>();
        final List<SerializedPage> serialized = new ArrayList<>();
        for (RoqFrontMatterPublishNormalPageBuildItem page : pages) {
            serialized.add(SerializedPage.ofNormal(page.url(), page.source(), page.data(), page.paginator()));
            pagesProducer.produce(new RoqFrontMatterRecordedPageBuildItem(page.source().id(), page.url(), false));
            if (page.source().isSiteIndex()) {
                siteIndexPages.add(page);
            }
        }
        if (siteIndexPages.size() > 1) {
            String paths = siteIndexPages.stream()
                    .map(p -> "'" + p.source().path() + "'")
                    .collect(Collectors.joining(", "));
            throw new RoqSiteIndexNotFoundException(
                    RoqException.builder("Multiple site index pages found")
                            .detail("Found %d index pages: %s".formatted(siteIndexPages.size(), paths))
                            .hint("Remove the extra index files so only a single site index remains."));
        }
        String resourcePath = RESOURCE_PREFIX + "normal-pages.ser";
        resourceProducer.produce(new LargeRecordBuildItem(resourcePath, LargeRecords.serialize(serialized),
                SerializedPage.SERIALIZABLE_CLASSES));
        final Supplier<List<NormalPage>> recordedPages = recorder.createPages(resourcePath);
        normalPagesProducer.produce(
                new RoqFrontMatterRecordedNormalPagesBuildItem(recordedPages, pages.size(), !siteIndexPages.isEmpty()));
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    RoqFrontMatterOutputBuildItem bindSite(
            LaunchModeBuildItem launchMode,
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            RoqFrontMatterRecordedNormalPagesBuildItem normalPagesItem,
            List<RoqFrontMatterRecordedCollectionBuildItem> collectionItems,
            List<RoqFrontMatterRecordedPageBuildItem> pageItems,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            BuildProducer<LargeRecordBuildItem> resourceProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return null;
        }
        if (normalPagesItem == null || !normalPagesItem.hasSiteIndex()) {
            if ((normalPagesItem == null || normalPagesItem.isEmpty()) && collectionItems.isEmpty()) {
                return null;
            }
            throw new RoqSiteIndexNotFoundException(
                    RoqException.builder("Site index not found")
                            .detail("No site index page (index.html, index.md, etc.) was found.")
                            .hint("Create an index file in your content or templates directory."));
        }
        final Map<ConfiguredCollection, Supplier<List<DocumentPage>>> collectionsMap = collectionItems.stream().collect(
                Collectors.toMap(RoqFrontMatterRecordedCollectionBuildItem::collection,
                        RoqFrontMatterRecordedCollectionBuildItem::documents));
        final Supplier<RoqCollections> collectionsSupplier = recorder.createRoqCollections(collectionsMap);
        final Supplier<Site> siteSupplier = recorder.createSite(normalPagesItem.pages(), collectionsSupplier);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Site.class)
                .named("site")
                .scope(Singleton.class)
                .unremovable()
                .supplier(siteSupplier)
                .done());
        Map<String, RoqFrontMatterRecordedPageBuildItem> itemsMap = new HashMap<>();
        Map<String, String> pageIdsByPath = new HashMap<>();
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
                            RoqException.builder("Path conflict")
                                    .detail("Duplicate path '%s' produced by both '%s' and '%s'.".formatted(
                                            i.url().resourcePath(), prev.id(), i.id()))
                                    .hint("Ensure each page resolves to a unique URL path, or use 'link:' in front matter to customize the output path."));
                }
            }
            pageIdsByPath.put(i.url().resourcePath(), i.id());
        }
        String pageIdsByPathResource = RESOURCE_PREFIX + "page-ids-by-path.ser";
        resourceProducer.produce(
                new LargeRecordBuildItem(pageIdsByPathResource, LargeRecords.serialize(pageIdsByPath)));
        return new RoqFrontMatterOutputBuildItem(pageIdsByPath, pageIdsByPathResource);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    public List<RouteBuildItem> produceRoutes(RoqSiteConfig config, RoqFrontMatterRecorder recorder,
            HttpRootPathBuildItem httpRootPath, RoqFrontMatterOutputBuildItem roqFrontMatterOutput) {
        if (roqFrontMatterOutput == null || roqFrontMatterOutput.pageIdsByPath().isEmpty()) {
            return List.of();
        }
        String routePath = httpRootPath.relativePath(StringPaths.join(config.pathPrefixOrEmpty(), "/*"));
        RouteBuildItem resolverRoute = httpRootPath.routeBuilder()
                .routeFunction(routePath, recorder.initializeResolverRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.pageResolver(roqFrontMatterOutput.pageIdsByPathResource()))
                .build();
        RouteBuildItem renderRoute = httpRootPath.routeBuilder()
                .routeFunction(routePath, recorder.initializeRenderRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.renderHandler())
                .build();
        return List.of(resolverRoute, renderRoute);
    }
}
