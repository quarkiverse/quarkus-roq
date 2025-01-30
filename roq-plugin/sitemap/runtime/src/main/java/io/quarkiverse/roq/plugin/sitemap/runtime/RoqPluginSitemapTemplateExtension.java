package io.quarkiverse.roq.plugin.sitemap.runtime;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
@Unremovable
public class RoqPluginSitemapTemplateExtension {

    public static final String LAST_MODIFIED_AT = "last-modified-at";

    public static ZonedDateTime lastModifiedAt(Page page) {
        if (page.data().containsKey(LAST_MODIFIED_AT)) {
            return ZonedDateTime.parse(page.data().getString(LAST_MODIFIED_AT));
        }
        return page.date();
    }

    public static boolean sitemap(Page page) {
        return page.data().getBoolean("sitemap", true);
    }

    public static String iso(ZonedDateTime date) {
        return date.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

}
