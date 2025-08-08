package io.quarkiverse.roq.plugin.lunr.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.DocumentPage;
import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonObject;

@TemplateExtension
@Unremovable
public class RoqPluginLunrTemplateExtension {

    public static final String INDEX_BOOST_KEY = "search-boost";

    public static boolean search(Page page) {
        return page.source().isTargetHtml() && page.data().getBoolean("search", true);
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
        final JsonObject baseDoc = new JsonObject()
                .put("summary", page.description());
        if (page.data().containsKey("tags")) {
            final List<String> tags = RoqTemplateExtension.asStrings(page.data("tags"));
            baseDoc.put("tags", tags);
        }
        final long baseBoost = page.data().containsKey(INDEX_BOOST_KEY)
                ? page.data().getLong(INDEX_BOOST_KEY)
                : 1;
        final String absoluteUrl = page.url().absolute();
        if (!anchors.isEmpty()) {
            for (Anchor a : anchors) {
                if (a.content().isBlank()) {
                    continue;
                }
                final JsonObject d = baseDoc.copy()
                        .put("content", a.content())
                        .put("title", page.title() + " - " + a.title())
                        .put("url", absoluteUrl + "#" + a.id())
                        .put("fragment", a.id())
                        .put("boost", baseBoost + a.boost());
                map.put(page.id() + "#" + a.id(), d);
            }
        }
        baseDoc.put("url", absoluteUrl)
                .put("title", page.title())
                .put("content", htmlDoc.text())
                .put("boost", baseBoost);
        map.put(page.id(), baseDoc);
        return map;
    }

    static List<Anchor> extractAnchors(Document html) {
        return Stream.concat(extractAsciidocAnchors(html).stream(), extractMarkdownAnchors(html).stream()).toList();
    }

    private static List<Anchor> extractAsciidocAnchors(Document html) {
        List<Anchor> anchors = new ArrayList<>();

        for (Element section : html.select("div.sect1, div.sect2, div.sect3, div.sect4, div.sect5, div.sect6")) {
            Element heading = section.selectFirst("h1, h2, h3, h4, h5, h6");
            final String id;
            if (heading == null) {
                continue;
            }
            if (heading.hasAttr("id")) {
                id = heading.attr("id");
            } else if (section.hasAttr("id")) {
                id = section.attr("id");
            } else {
                continue;
            }

            String title = heading.text();

            // Clone the section so we can remove the heading before extracting content
            Element contentClone = section.clone();
            Element headingClone = contentClone.selectFirst(heading.tagName() + "#" + id);
            if (headingClone != null)
                headingClone.remove();

            String content = contentClone.text();

            if (content.isBlank()) {
                continue;
            }

            int boost = boostForTag(heading.tagName());
            anchors.add(new Anchor(id, title, content, boost));
        }

        return anchors;
    }

    public static List<Anchor> extractMarkdownAnchors(Document doc) {
        List<Anchor> anchors = new ArrayList<>();
        Elements headings = doc.select("h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]");

        for (Element heading : headings) {
            // Skip if the heading is inside an AsciiDoc section (sect1, sect2, ...)
            if (heading.parents().stream().anyMatch(p -> p.classNames().stream().anyMatch(c -> c.startsWith("sect")))) {
                continue;
            }

            String id = heading.id();
            String title = heading.text();
            int level = Integer.parseInt(heading.tagName().substring(1));
            int boost = level + 1;

            StringBuilder contentBuilder = new StringBuilder();
            Element current = heading.nextElementSibling();

            while (current != null) {
                if (current.tagName().matches("h[1-" + level + "]")) {
                    break; // Stop at next heading of same or higher level
                }
                contentBuilder.append(current.text()).append(" ");
                current = current.nextElementSibling();
            }

            String content = contentBuilder.toString().trim();

            if (content.isBlank()) {
                continue;
            }

            anchors.add(new Anchor(id, title, content, boost));
        }

        return anchors;
    }

    private static int boostForTag(String tagName) {
        if (tagName.matches("h[1-6]")) {
            return Integer.parseInt(tagName.substring(1)) + 1;
        }
        return 1;
    }

    record Anchor(String id, String title, String content, int boost) {
    }
}
