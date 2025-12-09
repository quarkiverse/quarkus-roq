package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqPathConflictException;
import io.quarkiverse.roq.frontmatter.deployment.exception.RoqSiteIndexNotFoundException;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterRecorder;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.LaunchModeBuildItem;
import io.quarkus.runtime.LaunchMode;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;

class RoqFrontMatterRecordProcessor {
    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterRecordProcessor.class);

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void bindSources(
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            List<RoqFrontMatterRawTemplateBuildItem> rawTemplates,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            RoqFrontMatterRecorder recorder) {
        if (rootUrlItem == null) {
            return;
        }
        final List<TemplateSource> list = rawTemplates.stream().map(RoqFrontMatterRawTemplateBuildItem::templateSource)
                .toList();
        Supplier<Sources> sources = recorder.createSources(list);
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
                String sourceFilePath = null;
                if (item.source() != null && item.source().file() != null) {
                    sourceFilePath = item.source().file().absolutePath();
                }
                pagesProducer
                        .produce(
                                new RoqFrontMatterRecordedPageBuildItem(item.source().id(), url, item.collection().hidden(),
                                        document, sourceFilePath));
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
            String sourceFilePath = null;
            if (page.source() != null && page.source().file() != null) {
                sourceFilePath = page.source().file().absolutePath();
            }
            pagesProducer.produce(new RoqFrontMatterRecordedPageBuildItem(page.source().id(), page.url(), false, recordedPage,
                    sourceFilePath));
            normalPagesProducer
                    .produce(new RoqFrontMatterRecordedNormalPageBuildItem(page.source().id(), page.url(), recordedPage));
            if (page.source().isSiteIndex()) {
                indexPageProducer.produce(new RoqFrontMatterRecordedSiteIndexBuildItem(recordedPage));
            }
        }
    }

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
        Map<String, RoqFrontMatterRecordedPageBuildItem> itemsMap = new HashMap<>();
        Map<String, Supplier<? extends Page>> allPagesByPath = new HashMap<>();
        Map<String, String> sourceFilePathsByPath = new HashMap<>();
        for (RoqFrontMatterRecordedPageBuildItem i : pageItems) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debugf("Published %spage '%s' on '%s'", i.hidden() ? "hidden " : "", i.id(), i.url().toString());
            }
            if (i.hidden()) {
                continue;
            }
            final RoqFrontMatterRecordedPageBuildItem prev = itemsMap.put(i.url().resourcePath(), i);
            allPagesByPath.put(i.url().resourcePath(), i.page());
            if (i.sourceFilePath() != null) {
                sourceFilePathsByPath.put(i.url().resourcePath(), i.sourceFilePath());
            }
            if (prev != null) {
                if (launchMode.getLaunchMode() == LaunchMode.DEVELOPMENT) {
                    LOGGER.warnf(
                            "Conflict detected: Duplicate path (%s) found in %s and %s. In development, the first occurrence will be kept, but this will cause an exception in normal mode.",
                            i.url().resourcePath(), prev.id(), i.id());
                } else {
                    throw new RoqPathConflictException(
                            "Conflict detected: Duplicate path (%s) found in %s and %s".formatted(i.url().resourcePath(),
                                    prev.id(),
                                    i.id()));
                }
            }
        }
        return new RoqFrontMatterOutputBuildItem(allPagesByPath, sourceFilePathsByPath);
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
                .routeFunction(httpRootPath.relativePath(PathUtils.join(config.pathPrefixOrEmpty(), "/*")),
                        recorder.initializeRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.handler(httpRootPath.getRootPath(), roqFrontMatterOutput.allPagesByPath()))
                .build();
    }
}
