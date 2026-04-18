package io.quarkiverse.roq.plugin.toc.runtime;

import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonObject;

/**
 * Qute template extension that provides table of contents generation for pages.
 */

@TemplateExtension
@Unremovable
public class RoqPluginTocTemplateExtension {

    static final Map<String, CachedToc> CACHE = new ConcurrentHashMap<>();

    record CachedToc(int contentHash, List<TocEntry> entries) {
    }

    /**
     * Returns a structured list of TOC entries extracted from the page's rendered HTML content.
     * Honors the {@code content-toc} (opt-out, default {@code true}) and {@code content-toc-levels}
     * (max heading tag level 1–6, default 6) frontmatter keys.
     * <p>
     * Usage in Qute templates: {@code {page.toc}}
     * <p>
     * The returned list and its entries should be treated as immutable by callers — they may be
     * shared across renders via an internal content-hash-keyed cache.
     */
    public static List<TocEntry> toc(Page page) {
        if (!isContentTocEnabled(page.data())) {
            return List.of();
        }
        String html = resolvePageContent(page);
        if (html == null || html.isBlank()) {
            return List.of();
        }
        List<TocEntry> fullEntries = extractTocFromHtmlCached(page, html);
        int maxLevel = page.data().getInteger("content-toc-levels", 6);
        return applyMaxLevel(fullEntries, maxLevel);
    }

    /**
     * Returns a pre-rendered HTML navigation block for the table of contents.
     * The {@code <nav>} receives an {@code aria-label} sourced from the {@code content-toc-title}
     * frontmatter key (defaults to {@code "Table of contents"}). Each {@code
     * <li>} carries a
     * {@code data-level} attribute with 0-indexed depth (h1 → 0, h2 → 1, …), matching the
     * convention used by the default theme's {@code toc.js}.
     * <p>
     * Usage in Qute templates: {@code {page.tocHtml}}
     */
    public static RawString tocHtml(Page page) {
        List<TocEntry> entries = toc(page);
        if (entries.isEmpty()) {
            return new RawString("");
        }
        String label = page.data().getString("content-toc-title", "Table of contents");
        return new RawString(renderTocHtml(entries, label));
    }

    private static String resolvePageContent(Page page) {
        Site site = Arc.container().instance(Site.class).get();
        return site.pageContent(page);
    }

    static List<TocEntry> extractTocFromHtmlCached(Page page, String html) {
        String key = page.url() != null ? page.url().toString() : null;
        int hash = html.hashCode();
        if (key != null) {
            CachedToc cached = CACHE.get(key);
            if (cached != null && cached.contentHash() == hash) {
                return cached.entries();
            }
        }
        List<TocEntry> entries = extractTocFromHtml(html);
        if (key != null) {
            CACHE.put(key, new CachedToc(hash, entries));
        }
        return entries;
    }

    static List<TocEntry> extractTocFromHtml(String html) {
        Document doc = Jsoup.parse(html);
        List<HeadingInfo> headings = extractHeadings(doc);
        return buildHierarchy(headings);
    }

    static boolean isContentTocEnabled(JsonObject data) {
        return data == null || data.getBoolean("content-toc", true);
    }

    /**
     * Returns a filtered copy of the entry tree including only entries whose heading level
     * is {@code <= maxLevel}. The original tree is not modified.
     */
    static List<TocEntry> applyMaxLevel(List<TocEntry> entries, int maxLevel) {
        if (maxLevel >= 6) {
            return entries;
        }
        int cap = Math.max(1, Math.min(6, maxLevel));
        List<TocEntry> filtered = new ArrayList<>();
        for (TocEntry entry : entries) {
            if (entry.level() > cap) {
                continue;
            }
            TocEntry copy = new TocEntry(entry.id(), entry.title(), entry.level());
            copy.children().addAll(applyMaxLevel(entry.children(), cap));
            filtered.add(copy);
        }
        return filtered;
    }

    static String renderTocHtml(List<TocEntry> entries, String label) {
        StringBuilder sb = new StringBuilder();
        sb.append("<nav class=\"roq-toc\" aria-label=\"").append(escapeAttr(label)).append("\">\n");
        renderEntries(sb, entries);
        sb.append("</nav>\n");
        return sb.toString();
    }

    static List<HeadingInfo> extractHeadings(Document doc) {
        List<HeadingInfo> headings = new ArrayList<>();

        // Extract from AsciiDoc sections (div.sect1, div.sect2, ...)
        Elements asciidocSections = doc.select("div.sect1, div.sect2, div.sect3, div.sect4, div.sect5, div.sect6");
        for (Element section : asciidocSections) {
            Element heading = section.selectFirst("h1, h2, h3, h4, h5, h6");
            if (heading == null) {
                continue;
            }
            String id;
            if (heading.hasAttr("id")) {
                id = heading.attr("id");
            } else if (section.hasAttr("id")) {
                id = section.attr("id");
            } else {
                continue;
            }
            int level = Integer.parseInt(heading.tagName().substring(1));
            headings.add(new HeadingInfo(id, heading.text(), level));
        }

        // Extract Markdown headings (h1[id], h2[id], ...) that are NOT inside AsciiDoc sections
        Elements markdownHeadings = doc.select("h1[id], h2[id], h3[id], h4[id], h5[id], h6[id]");
        for (Element heading : markdownHeadings) {
            if (heading.parents().stream()
                    .anyMatch(p -> p.classNames().stream().anyMatch(c -> c.matches("sect[1-6]")))) {
                continue;
            }
            int level = Integer.parseInt(heading.tagName().substring(1));
            headings.add(new HeadingInfo(heading.id(), heading.text(), level));
        }

        return headings;
    }

    static List<TocEntry> buildHierarchy(List<HeadingInfo> headings) {
        List<TocEntry> roots = new ArrayList<>();
        Deque<TocEntry> stack = new LinkedList<>();

        for (HeadingInfo h : headings) {
            TocEntry entry = new TocEntry(h.id(), h.title(), h.level());

            // Pop entries from the stack until we find a parent with a lower level
            while (!stack.isEmpty() && stack.peek().level() >= h.level()) {
                stack.pop();
            }

            if (stack.isEmpty()) {
                roots.add(entry);
            } else {
                stack.peek().children().add(entry);
            }
            stack.push(entry);
        }

        return roots;
    }

    private static void renderEntries(StringBuilder sb, List<TocEntry> entries) {
        sb.append("<ul>\n");
        for (TocEntry entry : entries) {
            sb.append("<li data-level=\"").append(entry.level() - 1).append("\">")
                    .append("<a href=\"#").append(escapeAttr(entry.id())).append("\">")
                    .append(escapeHtml(entry.title())).append("</a>");
            if (!entry.children().isEmpty()) {
                sb.append('\n');
                renderEntries(sb, entry.children());
            }
            sb.append("</li>\n");
        }
        sb.append("</ul>\n");
    }

    static String escapeHtml(String text) {
        return Entities.escape(text, new Document.OutputSettings().charset("UTF-8").escapeMode(Entities.EscapeMode.base));
    }

    static String escapeAttr(String text) {
        return escapeHtml(text).replace("\"", "&quot;");
    }

    record HeadingInfo(String id, String title, int level) {
    }
}
