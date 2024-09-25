package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGINATE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.runtime.NormalPage.*;
import static io.quarkiverse.roq.util.PathUtils.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
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
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.*;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
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
        if (roqOutput == null) {
            return;
        }
        final Set<String> docTemplates = new HashSet<>();
        final Set<String> pageTemplates = new HashSet<>();
        for (RoqFrontMatterBuildItem item : roqFrontMatterBuildItems) {
            final String name = removeExtension(item.templatePath());
            LOGGER.tracef("Name without extension %s", name);
            templatePathProducer.produce(TemplatePathBuildItem.builder().path(item.templatePath()).extensionInfo(FEATURE)
                    .content(item.generatedContent()).build());
            if (item.collection() != null) {
                docTemplates.add(item.templatePath());
            } else {
                pageTemplates.add(item.templatePath());
            }
        }

        if (config.generator()) {
            for (String path : roqOutput.all().keySet()) {
                selectedPathProducer.produce(new SelectedPathBuildItem(addTrailingSlash(path))); // We add a trailing slash to make it detected as a html page
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }

        validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
            if (docTemplates.contains(c.getTemplateId())) {
                c.addParameter("page", DocumentPage.class.getName());
                c.addParameter("site", Site.class.getName());
            } else if (pageTemplates.contains(c.getTemplateId())) {
                c.addParameter("page", NormalPage.class.getName());
                c.addParameter("site", Site.class.getName());
            }

        }));

    }

    @BuildStep
    void registerAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqTemplateExtension.class));
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqTemplateGlobal.class));
    }

    @BuildStep
    void registerForReflexion(BuildProducer<ReflectiveClassBuildItem> reflectiveClassBuildItemBuildProducer) {
        reflectiveClassBuildItemBuildProducer
                .produce(ReflectiveClassBuildItem.builder(Page.class).methods().fields().queryMethods().build());
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    @Consume(SyntheticBeansRuntimeInitBuildItem.class)
    public RouteBuildItem produceRoute(RoqSiteConfig config, RoqFrontMatterRecorder recorder,
            HttpRootPathBuildItem httpRootPath, RoqFrontMatterOutputBuildItem roqFrontMatterOutput) {
        if (roqFrontMatterOutput == null || roqFrontMatterOutput.all().isEmpty()) {
            // There are no templates to serve
            return null;
        }
        return httpRootPath.routeBuilder()
                .routeFunction(httpRootPath.relativePath(removeTrailingSlash(config.rootPath()) + "/*"),
                        recorder.initializeRoute())
                .handlerType(HandlerType.BLOCKING)
                .handler(recorder.handler(httpRootPath.getRootPath(), roqFrontMatterOutput.all()))
                .build();
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    RoqFrontMatterOutputBuildItem bindFrontMatterData(HttpBuildTimeConfig httpConfig, RoqSiteConfig config,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqFrontMatterBuildItem> roqFrontMatterBuildItems,
            RoqFrontMatterRecorder recorder) throws BuildException {
        if (roqFrontMatterBuildItems.isEmpty()) {
            return null;
        }
        final var byKey = roqFrontMatterBuildItems.stream()
                .collect(Collectors.toMap(RoqFrontMatterBuildItem::key, Function.identity()));
        final var collectionsInfo = new HashMap<String, List<PageInfo>>();
        final Map<String, JsonObject> pagesInfo = new HashMap<>();
        final Map<String, Supplier<? extends Page>> all = new HashMap<>();
        final List<Supplier<NormalPage>> pages = new ArrayList<>();
        final RootUrl rootUrl = new RootUrl(config.urlOptional().orElse(""), httpConfig.rootPath);
        // First we prepare info to:
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
            if (item.collection() != null) {
                data.put(COLLECTION_KEY, item.collection());
                collectionsInfo.computeIfAbsent(item.collection(), k -> new ArrayList<>())
                        .add(new PageInfo(name, data));
            } else {
                pagesInfo.put(name, data);
            }

        }

        // Then we bind collections
        final Map<String, List<Supplier<DocumentPage>>> collectionSuppliers = new HashMap<>();
        for (Map.Entry<String, List<PageInfo>> c : collectionsInfo.entrySet()) {
            final int collectionSize = c.getValue().size();
            for (int i = 0; i < collectionSize; i++) {
                PageInfo p = c.getValue().get(i);
                Integer prev = i > 0 ? i - 1 : null;
                Integer next = i < collectionSize - 1 ? i + 1 : null;
                p.data.put(PREVIOUS_INDEX_KEY, prev);
                p.data.put(NEXT_INDEX_KEY, next);
                final String link = p.data().getString(LINK_KEY);
                final Supplier<DocumentPage> s = recorder.createDocument(rootUrl, p.name(), p.data());
                all.put(link, s);
                collectionSuppliers.computeIfAbsent(p.data().getString(COLLECTION_KEY), k -> new ArrayList<>())
                        .add(s);
            }
        }

        AtomicReference<Supplier<NormalPage>> index = new AtomicReference<>();

        // Then we bind pages
        for (Map.Entry<String, JsonObject> item : pagesInfo.entrySet()) {
            final String name = item.getKey();
            final JsonObject data = item.getValue();
            if (item.getValue().containsKey(PAGINATE_KEY)) {
                Paginate paginate = readPaginate(name, data);
                List<PageInfo> collection = collectionsInfo.get(paginate.collection());
                if (collection == null) {
                    throw new ConfigurationException(
                            "Paginate collection not found '" + paginate.collection() + "' in " + name);
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
                    bindPage(recorder, name, pages, all, index, rootUrl, link, data, paginator);
                }
            } else {
                final String link = data.getString(LINK_KEY);
                bindPage(recorder, name, pages, all, index, rootUrl, link, data, null);
            }

        }

        // Create Site bean
        if (index.get() == null) {
            throw new BuildException("Roq site must declare an index.html page");
        }

        final Supplier<RoqCollections> collectionsSupplier = recorder.createRoqCollections(collectionSuppliers);
        final Supplier<Site> siteSupplier = recorder.createSite(index.get(), pages, collectionsSupplier);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Site.class)
                .scope(Singleton.class)
                .unremovable()
                .supplier(siteSupplier)
                .done());

        return new RoqFrontMatterOutputBuildItem(all, collectionsSupplier);
    }

    private static void bindPage(RoqFrontMatterRecorder recorder,
            String name,
            List<Supplier<NormalPage>> pages,
            Map<String, Supplier<? extends Page>> all,
            AtomicReference<Supplier<NormalPage>> index,
            RootUrl rootUrl,
            String link,
            JsonObject data,
            Paginator paginator) {
        var p = recorder.createPage(rootUrl, name, data, paginator);
        all.put(link, p);
        pages.add(p);
        if (name.equals("index")) {
            index.set(p);
        }
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

    private record PageInfo(String name, JsonObject data) {
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