package io.quarkiverse.roq.plugin.sitemap.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAST_MODIFIED_AT;
import static io.quarkiverse.roq.plugin.sitemap.runtime.RoqSitemapKeys.SITEMAP;

import java.time.ZonedDateTime;
import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
@Unremovable
public class RoqPluginSitemapTemplateExtension {

    public static ZonedDateTime lastModifiedAt(Page page) {
        if (page.data().containsKey(LAST_MODIFIED_AT)) {
            return ZonedDateTime.parse(page.data().getString(LAST_MODIFIED_AT));
        }
        final ZonedDateTime date = page.date();
        return date != null ? date : ZonedDateTime.now();
    }

    public static boolean sitemap(Page page) {
        return page.data().getBoolean(SITEMAP, true);
    }

    /**
     * The pages to list in the sitemap, excluding the one the sitemap itself is rendered from.<br>
     * Example: "{#for p in site.sitemapPages(page)}".
     */
    public static List<NormalPage> sitemapPages(Site site, Page current) {
        return site.pages().stream()
                .filter(p -> p.source().isTargetHtml() && sitemap(p) && !p.id().equals(current.id()))
                .toList();
    }

    /**
     * The documents to list in the sitemap for this collection.<br>
     * Example: "{#for doc in collection.sitemapDocuments}".
     */
    public static List<DocumentPage> sitemapDocuments(RoqCollection collection) {
        return collection.stream().filter(RoqPluginSitemapTemplateExtension::sitemap).toList();
    }

    /**
     * The collections to list in the sitemap: the visible ones that have at least one document to show.<br>
     * Example: "{#for collection in site.sitemapCollections}".
     */
    public static List<RoqCollection> sitemapCollections(Site site) {
        return site.collections().list().stream()
                .filter(c -> !c.hidden() && !c.derived() && !sitemapDocuments(c).isEmpty())
                .toList();
    }

}
