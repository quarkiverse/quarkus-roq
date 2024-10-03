package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGE_LINK_TEMPLATE;
import static io.quarkiverse.roq.frontmatter.deployment.Link.DEFAULT_PAGINATE_LINK_TEMPLATE;
import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.prefixWithSlash;
import static io.quarkiverse.roq.util.PathUtils.removeTrailingSlash;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import io.quarkiverse.roq.frontmatter.deployment.Link.LinkData;
import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.RoqFrontMatterOutputBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterRecorder;
import io.quarkiverse.roq.frontmatter.runtime.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateGlobal;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollections;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeansRuntimeInitBuildItem;
import io.quarkus.builder.BuildException;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.Consume;
import io.quarkus.deployment.annotations.ExecutionTime;
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

    private static final String FEATURE = "roq-frontmatter";

    private static final String LINK_KEY = "link";
    private static final String PAGINATE_KEY = "paginate";
    private static final String COLLECTION_KEY = "collection";

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
            templatePathProducer
                    .produce(TemplatePathBuildItem.builder().path(item.info().generatedTemplatePath()).extensionInfo(FEATURE)
                            .content(item.generatedContent()).build());
            if (item.published()) {
                if (item.collection() != null) {
                    docTemplates.add(item.info().generatedTemplatePath());
                } else {
                    pageTemplates.add(item.info().generatedTemplatePath());
                }
            }

        }

        if (config.generator()) {
            for (String path : roqOutput.all().keySet()) {
                selectedPathProducer.produce(new SelectedPathBuildItem(addTrailingSlash(path), null)); // We add a trailing slash to make it detected as a html page
                notFoundPageDisplayableEndpointProducer
                        .produce(new NotFoundPageDisplayableEndpointBuildItem(prefixWithSlash(path)));
            }
        }

        validationParserHookProducer.produce(new ValidationParserHookBuildItem(c -> {
            if (docTemplates.contains(c.getTemplateId())) {
                c.addParameter("page", DocumentPage.class.getName());
                c.addParameter("site", Site.class.getName());
                c.addParameter("tag", String.class.getName());
            } else if (pageTemplates.contains(c.getTemplateId())) {
                c.addParameter("page", NormalPage.class.getName());
                c.addParameter("site", Site.class.getName());
                c.addParameter("tag", String.class.getName());
            }

        }));

    }

    @BuildStep
    void registerAdditionalBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        additionalBeans.produce(AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        RoqTemplateExtension.class,
                        RoqTemplateGlobal.class,
                        Page.class,
                        RoqUrl.class,
                        RootUrl.class,
                        DocumentPage.class,
                        NormalPage.class,
                        RoqCollections.class,
                        RoqCollection.class,
                        Paginator.class)
                .setUnremovable().build());
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
    RoqFrontMatterOutputBuildItem bindFrontMatterSite(HttpBuildTimeConfig httpConfig, RoqSiteConfig config,
            BuildProducer<SyntheticBeanBuildItem> beansProducer,
            List<RoqFrontMatterBuildItem> roqFrontMatterBuildItems,
            RoqFrontMatterRecorder recorder) throws BuildException {
        if (roqFrontMatterBuildItems.isEmpty()) {
            return null;
        }
        final var byKey = roqFrontMatterBuildItems.stream()
                .collect(Collectors.toMap(RoqFrontMatterBuildItem::id, Function.identity()));
        final var collectionsToPublish = new HashMap<String, List<PageToPublish>>();
        final List<PageToPublish> pagesToPublish = new ArrayList<>();
        final Map<String, Supplier<? extends Page>> all = new HashMap<>();
        final List<Supplier<NormalPage>> pages = new ArrayList<>();
        final RootUrl rootUrl = new RootUrl(config.urlOptional().orElse(""), httpConfig.rootPath);
        // First we prepare info to:
        // - bind static pages
        // - detect paginated pages (to be added later)
        // - detect collections (to be added later)
        for (RoqFrontMatterBuildItem item : roqFrontMatterBuildItems) {
            if (!item.published()) {
                continue;
            }
            final JsonObject data = mergeParents(item, byKey);
            final String link = Link.link(config.rootPath(), data.getString(LINK_KEY, DEFAULT_PAGE_LINK_TEMPLATE),
                    new LinkData(item.info().baseFileName(), item.info().date(), item.collection(), null, data));
            if (item.collection() != null) {
                data.put(COLLECTION_KEY, item.collection());
                collectionsToPublish.computeIfAbsent(item.collection(), k -> new ArrayList<>())
                        .add(new PageToPublish(link, item.info(), data));
            } else {
                pagesToPublish.add(new PageToPublish(link, item.info(), data));
            }

        }

        // Then we bind collections
        final Map<String, List<Supplier<DocumentPage>>> collectionSuppliers = new HashMap<>();
        for (Map.Entry<String, List<PageToPublish>> c : collectionsToPublish.entrySet()) {
            final int collectionSize = c.getValue().size();
            for (int i = 0; i < collectionSize; i++) {
                final PageToPublish p = c.getValue().get(i);
                PageInfo info = p.info();
                Integer prev = i > 0 ? i - 1 : null;
                Integer next = i < collectionSize - 1 ? i + 1 : null;
                DocumentInfo doc = new DocumentInfo(c.getKey(), prev, next);
                final Supplier<DocumentPage> s = recorder.createDocument(rootUrl.resolve(p.link()), info, doc,
                        p.data());
                all.put(p.link, s);
                collectionSuppliers.computeIfAbsent(p.data().getString(COLLECTION_KEY), k -> new ArrayList<>())
                        .add(s);
            }
        }

        AtomicReference<Supplier<NormalPage>> index = new AtomicReference<>();

        // Then we bind pages
        for (PageToPublish p : pagesToPublish) {
            if (p.data().containsKey(PAGINATE_KEY)) {

                Paginate paginate = readPaginate(p.info.id(), p.data);
                List<PageToPublish> collection = collectionsToPublish.get(paginate.collection());
                if (collection == null) {
                    throw new ConfigurationException(
                            "Paginate collection not found '" + paginate.collection() + "' in " + p.info.id());
                }
                final int total = collection.size();
                if (paginate.size() <= 0) {
                    throw new ConfigurationException("Page size must be greater than zero.");
                }
                int countPages = (total + paginate.size() - 1) / paginate.size();

                List<PageToPublish> paginatedPages = new ArrayList<>();
                final String linkTemplate = paginate.link() != null ? paginate.link() : DEFAULT_PAGINATE_LINK_TEMPLATE;
                for (int i = 1; i <= countPages; i++) {
                    final String link = i == 1 ? p.link()
                            : Link.link(config.rootPath(), linkTemplate, new LinkData(p.info().baseFileName(), p.info().date(),
                                    paginate.collection(), Integer.toString(i), p.data()));
                    PageInfo info = p.info();
                    if (i > 1) {
                        info = info.changeId(Link.link(config.rootPath(), linkTemplate,
                                new LinkData(p.info().baseFileName(), p.info().date(),
                                        paginate.collection(), Integer.toString(i), p.data())));
                    }
                    paginatedPages.add(new PageToPublish(link, info, p.data()));
                }

                for (int i = 1; i <= countPages; i++) {
                    Integer prev = i > 1 ? i - 1 : null;
                    Integer next = i <= countPages - 1 ? i + 1 : null;
                    PageToPublish currentPage = paginatedPages.get(i - 1);
                    PageToPublish previousPage = null;
                    PageToPublish nextPage = null;
                    RoqUrl previousUrl = null;
                    RoqUrl nextUrl = null;
                    if (prev != null) {
                        previousPage = paginatedPages.get(prev - 1);
                        previousUrl = rootUrl.resolve(previousPage.link());
                    }
                    if (next != null) {
                        nextPage = paginatedPages.get(next - 1);
                        nextUrl = rootUrl.resolve(nextPage.link());
                    }
                    Paginator paginator = new Paginator(paginate.collection(), total, paginate.size(), countPages, i, prev,
                            previousUrl, next, nextUrl);

                    bindPage(recorder, pages, all, index, rootUrl, currentPage, currentPage.link(), paginator);
                }
            } else {
                bindPage(recorder, pages, all, index, rootUrl, p, p.link, null);
            }

        }

        // Create Site bean
        if (index.get() == null) {
            throw new BuildException("Roq site must declare an index.html page");
        }

        final Supplier<RoqCollections> collectionsSupplier = recorder.createRoqCollections(collectionSuppliers);
        final Supplier<Site> siteSupplier = recorder.createSite(rootUrl, index.get(), pages, collectionsSupplier);
        beansProducer.produce(SyntheticBeanBuildItem.configure(Site.class)
                .scope(Singleton.class)
                .unremovable()
                .supplier(siteSupplier)
                .done());

        return new RoqFrontMatterOutputBuildItem(all, collectionsSupplier);
    }

    private static void bindPage(RoqFrontMatterRecorder recorder,
            List<Supplier<NormalPage>> pages,
            Map<String, Supplier<? extends Page>> all,
            AtomicReference<Supplier<NormalPage>> index,
            RootUrl rootUrl,
            PageToPublish page,
            String link,
            Paginator paginator) {
        var p = recorder.createPage(rootUrl.resolve(link), page.info, page.data, paginator);
        all.put(link, p);
        pages.add(p);
        if (page.info.id().equals("index")) {
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

    private record PageToPublish(String link, PageInfo info, JsonObject data) {
    }

    private static JsonObject mergeParents(RoqFrontMatterBuildItem item, Map<String, RoqFrontMatterBuildItem> byPath) {
        Stack<JsonObject> fms = new Stack<>();
        String parent = item.layout();
        fms.add(item.data());
        while (parent != null) {
            if (byPath.containsKey(parent)) {
                final RoqFrontMatterBuildItem parentItem = byPath.get(parent);
                parent = parentItem.layout();
                fms.push(parentItem.data());
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
