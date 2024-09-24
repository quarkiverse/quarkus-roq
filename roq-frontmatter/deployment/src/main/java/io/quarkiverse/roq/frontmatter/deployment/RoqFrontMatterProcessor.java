package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGINATE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.runtime.Page.*;
import static io.quarkiverse.roq.util.PathUtils.*;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.*;
import io.quarkiverse.roq.frontmatter.runtime.RoqCollection.Paginator;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.qute.deployment.TemplatePathBuildItem;
import io.quarkus.qute.deployment.ValidationParserHookBuildItem;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.quarkus.vertx.http.deployment.HttpRootPathBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;
import io.quarkus.vertx.http.runtime.HttpBuildTimeConfig;
import io.vertx.core.json.JsonObject;

class RoqFrontMatterProcessor {

    private static final Logger LOGGER = org.jboss.logging.Logger.getLogger(RoqFrontMatterProcessor.class);
    private static final String FEATURE = "roq-frontmatter";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    void generateTemplate(
            RoqFrontMatterConfig config,
            BuildProducer<TemplatePathBuildItem> templatePathProducer,
            BuildProducer<ValidationParserHookBuildItem> validationParserHookProducer,
            BuildProducer<SelectedPathBuildItem> selectedPathProducer,
            BuildProducer<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpointProducer,
            List<RoqFrontMatterBuildItem> roqFrontMatterBuildItems,
            RoqFrontMatterOutputBuildItem roqOutput) {
        final Set<String> templates = new HashSet<>();
        for (RoqFrontMatterBuildItem item : roqFrontMatterBuildItems) {
            final String name = removeExtension(item.templatePath());
            LOGGER.tracef("Name without extension %s", name);
            templatePathProducer.produce(TemplatePathBuildItem.builder().path(item.templatePath()).extensionInfo(FEATURE)
                    .content(item.generatedContent()).build());
            templates.add(item.templatePath());
        }

        if (config.generator()) {
            for (String path : roqOutput.pages().keySet()) {
                selectedPathProducer.produce(new SelectedPathBuildItem(addTrailingSlash(path))); // We add a trailing slash to make it detected as a html page
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }

        validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
            if (templates.contains(c.getTemplateId())) {
                c.addParameter("page", Page.class.getName());
                c.addParameter("site", Page.class.getName());
                c.addParameter("collections", RoqCollections.class.getName());
            }
        }));

    }

    @BuildStep
    void registerAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqTemplateExtension.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqTemplateGlobal.class));
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    public RouteBuildItem produceRoute(RoqSiteConfig config, RoqFrontMatterRecorder recorder,
            HttpRootPathBuildItem httpRootPath, RoqFrontMatterOutputBuildItem roqFrontMatterOutput) {
        if (roqFrontMatterOutput.pages().isEmpty()) {
            // There are no templates to serve
            return null;
        }
        return httpRootPath.routeBuilder()
                .routeFunction(httpRootPath.relativePath(removeTrailingSlash(config.rootPath()) + "/*"),
                        recorder.initializeRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.handler(httpRootPath.getRootPath(),
                        roqFrontMatterOutput.roqCollectionsSupplier(), roqFrontMatterOutput.pages()))
                .build();
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    RoqFrontMatterOutputBuildItem bindFrontMatterData(HttpBuildTimeConfig httpConfig, RoqSiteConfig config,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqFrontMatterBuildItem> roqFrontMatterBuildItems,
            RoqFrontMatterRecorder recorder) {
        final var byKey = roqFrontMatterBuildItems.stream()
                .collect(Collectors.toMap(RoqFrontMatterBuildItem::key, Function.identity()));
        final var collections = new HashMap<String, List<CollectionPage>>();
        final Map<String, Supplier<Page>> pages = new HashMap<>();
        final Map<String, JsonObject> paginationItems = new HashMap<>();
        final RootUrl rootUrl = new RootUrl(config.urlOptional().orElse(""), httpConfig.rootPath);
        // First we prepare data and links to:
        // - bind static pages
        // - detect paginated pages (to be added later)
        // - detect collections (to be added later)
        for (RoqFrontMatterBuildItem item : roqFrontMatterBuildItems) {
            if (!item.visible()) {
                continue;
            }
            final String name = item.key();
            final JsonObject data = mergeParents(item, byKey);
            final String link = Link.link(config.rootPath(), data.getString(LINK_KEY, DEFAULT_PAGE_LINK_TEMPLATE), data);
            data.put(LINK_KEY, link);
            if (data.containsKey(PAGINATE_KEY)) {
                paginationItems.put(name, data);
            } else {
                if (item.collection() != null) {
                    data.put(COLLECTION_KEY, item.collection());
                    collections.computeIfAbsent(item.collection(), k -> new ArrayList<>())
                            .add(new CollectionPage(item.collection(), link, name, data));
                } else {
                    bindPage(config, beansProducer, recorder, pages, rootUrl, name.equals("index"), name, link, data, null);
                }
            }
        }

        // Then we bind collections
        final Map<String, List<Supplier<Page>>> collectionSuppliers = new HashMap<>();
        for (Map.Entry<String, List<CollectionPage>> c : collections.entrySet()) {
            final int collectionSize = c.getValue().size();
            for (int i = 0; i < collectionSize; i++) {
                CollectionPage p = c.getValue().get(i);
                Integer prev = i > 0 ? i - 1 : null;
                Integer next = i < collectionSize - 1 ? i + 1 : null;
                p.data.put(PREVIOUS_INDEX_KEY, prev);
                p.data.put(NEXT_INDEX_KEY, next);
                collectionSuppliers.computeIfAbsent(p.collection, k -> new ArrayList<>())
                        .add(bindPage(config, beansProducer, recorder, pages, rootUrl, false, p.name, p.link, p.data, null));
            }
        }

        final Supplier<RoqCollections> collectionsSupplier = recorder.createRoqCollections(collectionSuppliers);
        beansProducer.produce(SyntheticBeanBuildItem.configure(RoqCollections.class)
                .scope(Singleton.class)
                .unremovable()
                .supplier(collectionsSupplier)
                .done());

        // Then we bind paginations
        for (Map.Entry<String, JsonObject> item : paginationItems.entrySet()) {
            final String name = item.getKey();
            final JsonObject data = item.getValue();
            Paginate paginate = readPaginate(name, data);
            List<CollectionPage> collection = collections.get(paginate.collection());
            if (collection == null) {
                throw new ConfigurationException("Paginate collection not found '" + paginate.collection() + "' in " + name);
            }
            final int total = collection.size();
            if (paginate.size() <= 0) {
                throw new ConfigurationException("Page size must be greater than zero.");
            }
            int countPages = (total + paginate.size() - 1) / paginate.size();
            for (int i = 1; i <= countPages; i++) {
                Integer prev = i > 1 ? i - 1 : null;
                Integer next = i <= countPages - 1 ? i + 1 : null;

                final String linkTemplate = paginate.link() != null ? paginate.link() : DEFAULT_PAGINATE_LINK_TEMPLATE;
                final JsonObject d = data.copy().put(COLLECTION_KEY, paginate.collection());
                PageUrl previousUrl = prev == null ? null
                        : new PageUrl(rootUrl, Link.link(config.rootPath(), linkTemplate, d.put("page", prev)));
                PageUrl nextUrl = next == null ? null
                        : new PageUrl(rootUrl, Link.link(config.rootPath(), linkTemplate, d.put("page", next)));
                Paginator paginator = new Paginator(paginate.collection(), total, paginate.size(), countPages, i, prev,
                        previousUrl, next, nextUrl);
                final String link = i == 1 ? data.getString(LINK_KEY)
                        : Link.link(config.rootPath(), linkTemplate, d.put("page", i));
                final boolean isIndex = name.equals("index") && i == 1;
                bindPage(config, beansProducer, recorder, pages, rootUrl, isIndex, name, link, data, paginator);
            }

        }

        return new RoqFrontMatterOutputBuildItem(pages, collectionsSupplier);
    }

    private static Paginate readPaginate(String name, JsonObject data) {
        final Object value = data.getValue(PAGINATE_KEY);
        if (value instanceof JsonObject paginate) {
            final String collection = paginate.getString("collection");
            if (collection == null) {
                throw new ConfigurationException("Invalid pagination configuration in " + name);
            }
            return new Paginate(paginate.getInteger("size", 5), paginate.getString("link", DEFAULT_PAGINATE_LINK_TEMPLATE),
                    collection);
        }
        if (value instanceof String) {
            return new Paginate(5, DEFAULT_PAGINATE_LINK_TEMPLATE, (String) value);
        }
        throw new ConfigurationException("Invalid pagination configuration in " + name);
    }

    private record CollectionPage(String collection, String link, String name, JsonObject data) {
    }

    private static Supplier<Page> bindPage(RoqSiteConfig config, BuildProducer<SyntheticBeanBuildItem> beansProducer,
            RoqFrontMatterRecorder recorder,
            Map<String, Supplier<Page>> pages,
            RootUrl rootUrl,
            boolean isSiteIndex,
            String id,
            String link,
            JsonObject data,
            Paginator paginator) {

        final Supplier<Page> pageSupplier = recorder.createPage(rootUrl, id, data, paginator);
        final String name = prefixWithSlash(link);
        LOGGER.info("Creating synthetic bean for page with name " + name);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Page.class)
                .scope(Singleton.class)
                .unremovable()
                .named(name)
                .supplier(pageSupplier)
                .done());
        if (isSiteIndex) {
            LOGGER.info("Creating synthetic bean for site index");
            beansProducer.produce(SyntheticBeanBuildItem.configure(Page.class)
                    .scope(Singleton.class)
                    .unremovable()
                    .named("site")
                    .supplier(pageSupplier)
                    .done());
        }
        pages.put(link, pageSupplier);
        return pageSupplier;
    }

    private static JsonObject mergeParents(RoqFrontMatterBuildItem item, Map<String, RoqFrontMatterBuildItem> byPath) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.fm());
        while (parent != null) {
            if (byPath.containsKey(parent)) {
                final RoqFrontMatterBuildItem parentItem = byPath.get(parent);
                parent = parentItem.layout();
                fms.push(parentItem.fm());
            } else {
                parent = null;
            }
        }

        JsonObject merged = new JsonObject();
        while (!fms.empty()) {
            merged.mergeIn(fms.pop());
        }
        return merged;
    }

}