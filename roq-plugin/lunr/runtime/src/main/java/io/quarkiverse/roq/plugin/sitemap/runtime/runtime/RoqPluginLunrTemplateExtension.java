package io.quarkiverse.roq.plugin.sitemap.runtime.runtime;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
@Unremovable
public class RoqPluginLunrTemplateExtension {

    public static boolean search(Page page) {
        return page.data().getBoolean("search", true);
    }

}
