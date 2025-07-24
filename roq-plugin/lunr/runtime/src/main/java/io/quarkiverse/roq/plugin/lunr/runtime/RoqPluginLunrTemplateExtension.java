package io.quarkiverse.roq.plugin.lunr.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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
                        createPageJsonObjects(site, doc).forEach(json::put);
                    }
                }
            }
        }
        for (NormalPage page : site.pages()) {
            if (search(page)) {
                createPageJsonObjects(site, page).forEach(json::put);
            }
        }
        return new RawString(json.toString());
    }

    private static Map<String, JsonObject> createPageJsonObjects(Site site, Page page) {
        final Map<String, JsonObject> map = new HashMap<>();
        final String html = site.pageContent(page);
        Document htmlDoc = Jsoup.parse(html);
        final List<Anchor> anchors = extractAnchors(htmlDoc);
        final JsonObject doc = new JsonObject()
                .put("summary", page.description())
                .put("url", page.url().absolute())
                .put("title", page.title())
                .put("content", htmlDoc.text());
        if (page.data().containsKey("tags")) {
            final List<String> tags = RoqTemplateExtension.asStrings(page.data("tags"));
            doc.put("tags", tags);
        }
        if (page.data().containsKey("index-boost")) {
            doc.put("boost", page.data().getLong("index-boost"));
        }
        if (!anchors.isEmpty()) {
            for (Anchor a : anchors) {
                final JsonObject d = doc.copy()
                        .put("content", a.content())
                        .put("title", page.title() + " - " + a.title())
                        .put("url", page.url().absolute() + "#" + a.id())
                        .put("fragment", a.id());
                if (page.data().containsKey("index-boost")) {
                    d.put("boost", page.data().getLong("index-boost") + 1);
                }
                map.put(page.id() + "#" + a.id(), d);
            }
        }
        map.put(page.id(), doc);
        return map;
    }

    private static List<Anchor> extractAnchors(Document html) {
        Elements withId = html.select("[id]");
        List<Anchor> anchors = new ArrayList<>();

        for (Element el : withId) {
            String id = el.id();
            String title;
            String content;

            if (el.tagName().matches("h[1-6]")) {
                // For headings, use parent text as content
                title = el.text();
                Element parent = el.parent();
                content = (parent != null) ? parent.text() : title;
            } else if (el.tagName().equals("div")) {
                // For divs, use their own text
                title = id;
                content = el.text();
            } else {
                // For other elements with id (e.g. <a id="...">), skip or handle case-by-case
                continue;
            }
            anchors.add(new Anchor(id, title, content));
        }
        return anchors;
    }

    record Anchor(String id, String title, String content) {
    }
}
