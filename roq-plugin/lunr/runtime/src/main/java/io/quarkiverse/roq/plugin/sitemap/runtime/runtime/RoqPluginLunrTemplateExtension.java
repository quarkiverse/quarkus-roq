package io.quarkiverse.roq.plugin.sitemap.runtime.runtime;

import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonObject;

@TemplateExtension
@Unremovable
public class RoqPluginLunrTemplateExtension {

    public static boolean search(Page page) {
        return page.info().isHtml() && page.data().getBoolean("search", true);
    }

    public static RawString searchIndex(Site site) {
        JsonObject json = new JsonObject();
        for (RoqCollection collection : site.collections().list()) {
            if (!collection.hidden() && !collection.derived()) {
                for (DocumentPage doc : collection) {
                    if (search(doc)) {
                        final JsonObject d = createPageJsonObject(doc);
                        if (doc.data().containsKey("tags")) {
                            final List<String> tags = RoqTemplateExtension.asStrings(doc.data("tags"));
                            d.put("tags", tags);
                        }
                        json.put(doc.id(), d);
                    }
                }
            }
        }
        for (NormalPage page : site.pages()) {
            if (search(page)) {
                json.put(page.id(), createPageJsonObject(page));
            }
        }
        return new RawString(json.toString());
    }

    private static JsonObject createPageJsonObject(Page page) {
        return new JsonObject()
                .put("tags", page.url())
                .put("summary", page.description())
                .put("url", page.url().absolute())
                .put("title", page.title())
                .put("content", RoqTemplateExtension.stripHtml(RoqTemplateExtension.content(page)));
    }
}
