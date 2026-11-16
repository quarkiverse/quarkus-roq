package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The site model at build time: the same {@link Site}, {@link NormalPage} and {@link DocumentPage} objects the
 * application runs with (built from the pages recorded for runtime), with every page bound to the site so that
 * page expressions such as {@code page.image} or {@code post.collection} evaluate without a CDI container.
 * <p>
 * Not produced when the site has no index page (no site is recorded either).
 */
public final class RoqFrontMatterSiteModelBuildItem extends SimpleBuildItem {

    private final Site site;
    private final List<NormalPage> pages;
    private final Map<String, RoqCollection> collections;
    private final List<DocumentPage> documents;
    private final List<Page> allPages;

    public RoqFrontMatterSiteModelBuildItem(Site site, List<NormalPage> pages, Map<String, RoqCollection> collections,
            List<DocumentPage> documents) {
        this.site = site;
        this.pages = List.copyOf(pages);
        this.collections = Map.copyOf(collections);
        this.documents = List.copyOf(documents);
        List<Page> all = new ArrayList<>(pages.size() + documents.size());
        all.addAll(pages);
        all.addAll(documents);
        this.allPages = List.copyOf(all);
    }

    public Site site() {
        return site;
    }

    /**
     * @return the normal (non collection) pages, site index included
     */
    public List<NormalPage> pages() {
        return pages;
    }

    /**
     * @return the collections by id, derived collections included
     */
    public Map<String, RoqCollection> collections() {
        return collections;
    }

    /**
     * @return every document once, even when it belongs to several collections
     */
    public List<DocumentPage> documents() {
        return documents;
    }

    /**
     * @return the normal pages followed by the documents
     */
    public List<Page> allPages() {
        return allPages;
    }
}
