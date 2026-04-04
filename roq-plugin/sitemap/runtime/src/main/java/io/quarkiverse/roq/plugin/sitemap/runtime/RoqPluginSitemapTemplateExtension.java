package io.quarkiverse.roq.plugin.sitemap.runtime;

import static io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys.LAST_MODIFIED_AT;
import static io.quarkiverse.roq.plugin.sitemap.runtime.RoqSitemapKeys.SITEMAP;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
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

    public static String iso(ZonedDateTime date) {
        return date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

}
