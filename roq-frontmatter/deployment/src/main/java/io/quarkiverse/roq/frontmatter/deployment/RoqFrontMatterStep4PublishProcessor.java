package io.quarkiverse.roq.frontmatter.deployment;

import static io.quarkiverse.roq.frontmatter.deployment.RoqFrontMatterStep3DataProcessor.PAGINATE_KEY;
import static io.quarkiverse.roq.frontmatter.deployment.util.TemplateLink.DEFAULT_PAGINATE_LINK_TEMPLATE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.logging.Logger;

import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterDocumentBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterPaginatePageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.data.RoqFrontMatterRootUrlBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDerivedCollectionBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.items.publish.RoqFrontMatterPublishNormalPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.util.TemplateLink;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.config.RoqSiteConfig;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.runtime.configuration.ConfigurationException;
import io.vertx.core.json.JsonObject;

class RoqFrontMatterStep4PublishProcessor {

    private static final Logger LOGGER = Logger.getLogger(RoqFrontMatterStep4PublishProcessor.class);

    // Publish document pages (collection members) — pass-through from Step3 to make them
    // available for pagination counting and Step5 recording
    @BuildStep
    public void publishCollections(
            List<RoqFrontMatterDocumentBuildItem> documentTemplates,
            BuildProducer<RoqFrontMatterPublishDocumentPageBuildItem> publishDocuments) {
        for (RoqFrontMatterDocumentBuildItem documentTemplate : documentTemplates) {
            publishDocuments.produce(new RoqFrontMatterPublishDocumentPageBuildItem(documentTemplate.url(),
                    documentTemplate.template().source(), documentTemplate.collection(), documentTemplate.data()));
        }
    }

    // Resolve pagination: compute page count from collection sizes, generate paginated URLs,
    // and publish each page variant with its Paginator (prev/next links, total count, etc.)
    @BuildStep
    public void paginatePublish(RoqSiteConfig config,
            RoqFrontMatterRootUrlBuildItem rootUrlItem,
            BuildProducer<RoqFrontMatterPublishNormalPageBuildItem> pagesProducer,
            List<RoqFrontMatterPaginatePageBuildItem> paginationList,
            List<RoqFrontMatterPublishDocumentPageBuildItem> documents,
            List<RoqFrontMatterPublishDerivedCollectionBuildItem> derivedCollections) {
        if (paginationList.isEmpty() || rootUrlItem == null) {
            return;
        }
        final RootUrl rootUrl = rootUrlItem.rootUrl();
        // Count documents per collection (including derived collections like tag pages)
        final Map<String, AtomicInteger> sizeByCollection = new HashMap<>();

        for (RoqFrontMatterPublishDocumentPageBuildItem document : documents) {
            sizeByCollection.computeIfAbsent(document.collection().id(), k -> new AtomicInteger()).incrementAndGet();
        }

        for (RoqFrontMatterPublishDerivedCollectionBuildItem derivedCollection : derivedCollections) {
            sizeByCollection.computeIfAbsent(derivedCollection.collection().id(), k -> new AtomicInteger())
                    .addAndGet(derivedCollection.documentIds().size());
        }

        for (RoqFrontMatterPaginatePageBuildItem pagination : paginationList) {
            final JsonObject data = pagination.data();
            Paginate paginate = readPaginate(pagination.source().path(), data,
                    pagination.defaultPaginatedCollection());
            AtomicInteger collectionSize = sizeByCollection.get(paginate.collection());
            if (collectionSize == null) {
                throw new ConfigurationException(
                        "Paginate collection not found '%s' in '%s'".formatted(paginate.collection(),
                                pagination.source().file().relativePath()));
            }
            final int total = collectionSize.get();
            if (paginate.size() <= 0) {
                throw new ConfigurationException("Page size must be greater than zero.");
            }
            int countPages = (total + paginate.size() - 1) / paginate.size();

            // First pass: build the list of paginated pages with their URLs.
            // Page 1 keeps the original URL; subsequent pages get generated URLs (e.g. /posts/page/2)
            List<PageToPublish> paginatedPages = new ArrayList<>();
            final String linkTemplate = paginate.link() != null ? paginate.link() : DEFAULT_PAGINATE_LINK_TEMPLATE;
            for (int i = 1; i <= countPages; i++) {
                final RoqUrl paginatedUrl;
                if (i == 1) {
                    paginatedUrl = pagination.url();
                } else {
                    final String link = TemplateLink.paginateLink(config.pathPrefixOrEmpty(), linkTemplate,
                            new TemplateLink.PaginateLinkData(pagination.source(),
                                    paginate.collection(), Integer.toString(i), data));
                    paginatedUrl = rootUrl.resolve(link);
                }
                PageSource pageSource = pagination.source();
                if (i > 1) {
                    pageSource = pageSource.generated(
                            StringPaths.removeExtension(pageSource.id()) + "_p" + i + "." + pageSource.extension());
                }

                paginatedPages.add(new PageToPublish(paginatedUrl, pageSource, data));
            }

            // Second pass: publish each page with its Paginator containing prev/next navigation
            final List<RoqUrl> pagesUrl = paginatedPages.stream().map(PageToPublish::url).toList();

            for (int i = 1; i <= countPages; i++) {
                Integer prev = i > 1 ? i - 1 : null;
                Integer next = i <= countPages - 1 ? i + 1 : null;
                PageToPublish currentPage = paginatedPages.get(i - 1);
                PageToPublish previousPage;
                PageToPublish nextPage;
                RoqUrl previousUrl = null;
                RoqUrl nextUrl = null;
                if (prev != null) {
                    previousPage = paginatedPages.get(prev - 1);
                    previousUrl = previousPage.url();
                }
                if (next != null) {
                    nextPage = paginatedPages.get(next - 1);
                    nextUrl = nextPage.url();
                }
                RoqUrl firstUrl = paginatedPages.get(0).url();
                Paginator paginator = new Paginator(paginate.collection(), total, paginate.size(), countPages, i, firstUrl,
                        prev, previousUrl, next, nextUrl, pagesUrl);
                pagesProducer.produce(new RoqFrontMatterPublishNormalPageBuildItem(currentPage.url(), currentPage.source(),
                        currentPage.data(), paginator));
            }
        }

    }

    private static Paginate readPaginate(String name, JsonObject data, ConfiguredCollection defaultCollection) {
        final Object value = data.getValue(PAGINATE_KEY);
        if (value instanceof JsonObject paginate) {
            final String collection = paginate.getString("collection",
                    defaultCollection == null ? null : defaultCollection.id());
            if (collection == null) {
                throw new ConfigurationException("Invalid pagination configuration in " + name);
            }
            return new Paginate(paginate.getInteger("size", 5), paginate.getString("link", DEFAULT_PAGINATE_LINK_TEMPLATE),
                    collection);
        }
        if (value instanceof String collection) {
            return new Paginate(5, DEFAULT_PAGINATE_LINK_TEMPLATE, collection);
        }
        if (value instanceof Boolean paginate && paginate) {
            if (defaultCollection == null) {
                throw new ConfigurationException("Invalid pagination configuration in " + name);
            }
            return new Paginate(5, DEFAULT_PAGINATE_LINK_TEMPLATE, defaultCollection.id());
        }
        throw new ConfigurationException("Invalid pagination configuration in " + name);
    }

    private record PageToPublish(RoqUrl url, PageSource source, JsonObject data) {
    }

    record Paginate(int size, String link, String collection) {
        Paginate {
            if (size < 1) {
                throw new IllegalArgumentException("Paginate size cannot be lower than 1");
            }
            if (link == null) {
                throw new IllegalArgumentException("Paginate link cannot be null");
            }
            if (collection == null) {
                throw new IllegalArgumentException("Paginate collection cannot be null");
            }
        }
    }

}
