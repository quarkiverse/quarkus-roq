package io.quarkiverse.roq.plugin.toc.runtime;

import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.logging.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.jsoup.select.Elements;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.arc.Arc;
import io.quarkus.arc.Unremovable;
import io.quarkus.qute.RawString;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonObject;

/**
 * Qute template extension that provides table of contents generation for pages.
 */

@Unremovable
public class RoqPluginTocTemplateExtension {

    private static final Logger LOG = Logger.getLogger(RoqPluginTocTemplateExtension.class);

    static final Map<String, CachedToc> CACHE = new ConcurrentHashMap<>();

    record CachedToc(int contentHash, List<TocEntry> entries) {
    }

    /**
     * Keys of the pages whose table of contents is being resolved on the current thread.
     * See {@link #toc(Page)}.
     */
    private static final ThreadLocal<Set<String>> RESOLVING = ThreadLocal.withInitial(HashSet::new);

    /** Markup name registered by the Roq AsciiDoc plugins. */
    static final String ASCIIDOC_MARKUP = "asciidoc";
    /** Asciidoctor's own default for {@code toclevels}: two section levels (h2 and h3). */
    static final int ASCIIDOC_DEFAULT_TOC_LEVELS = 2;
    /** Default for non-AsciiDoc pages: every heading level. */
    static final int DEFAULT_MAX_LEVEL = 6;
    /** Front matter key holding an explicit maximum heading tag level. */
    static final String CONTENT_TOC_LEVELS = "content-toc-levels";
    /**
     * Page data keys that can hold AsciiDoc document attributes, most specific first.
     * A page sets {@code asciidoc-attributes} itself and Roq passes it to Asciidoctor as an API
     * attribute, so it overrides the document header; {@code attributes} holds the attributes Roq
     * parsed from that header.
     */
    static final List<String> ASCIIDOC_ATTRIBUTE_KEYS = List.of("asciidoc-attributes", "attributes");

    // Matches an AsciiDoc attribute entry such as ":toclevels: 3", optionally soft-set as ":toclevels: 3@".
    // The optional "{|" allows for the Qute escape wrapper Roq adds around escaped page content.
    private static final Pattern ASCIIDOC_TOC_LEVELS = Pattern
            .compile("(?m)^(?:\\{\\|)?:toclevels:[ \\t]*(\\d+)@?[ \\t]*$");
    // The first section title ends the document header; attribute entries below it are body content.
    private static final Pattern ASCIIDOC_FIRST_SECTION = Pattern.compile("(?m)^={2,6}[ \\t]");

    /**
     * Returns a structured list of TOC entries extracted from the page's rendered HTML content.
     * Honors the {@code content-toc} (opt-out, default {@code true}) and {@code content-toc-levels}
     * (max heading tag level 1–6) frontmatter keys. When {@code content-toc-levels} is absent,
     * AsciiDoc pages fall back to their {@code toclevels} attribute (see {@link #resolveMaxLevel});
     * other pages include all six levels.
     * <p>
     * Usage in Qute templates: {@code {page.toc}}
     * <p>
     * The returned list and its entries should be treated as immutable by callers — they may be
     * shared across renders via an internal content-hash-keyed cache.
     * <p>
     * Building the list renders the page content, so a {@code {page.toc}} expression inside that
     * content re-enters this method for the same page. The re-entrant call returns an empty list,
     * which keeps the nested render finite; the outer call still returns the real entries.
     */
    @TemplateExtension
    public static List<TocEntry> toc(Page page) {
        if (!isContentTocEnabled(page.data())) {
            return List.of();
        }
        Set<String> resolving = RESOLVING.get();
        String pageKey = pageKey(page);
        if (!resolving.add(pageKey)) {
            LOG.debugf("Ignoring a re-entrant {page.toc} while rendering the content of '%s'", pageKey);
            return List.of();
        }
        try {
            String html = resolvePageContent(page);
            if (html == null || html.isBlank()) {
                return List.of();
            }
            List<TocEntry> fullEntries = extractTocFromHtmlCached(page, html);
            String markup = markupOf(page);
            int maxLevel = resolveMaxLevel(page.data(), markup, page::rawTemplate);
            if (LOG.isDebugEnabled()) {
                LOG.debugf("Table of contents for '%s': markup=%s, maxLevel=%s, headings=%s, page data keys=%s",
                        page.url(), markup, maxLevel, fullEntries.size(),
                        page.data() == null ? List.of() : page.data().fieldNames());
            }
            return applyMaxLevel(fullEntries, maxLevel);
        } finally {
            resolving.remove(pageKey);
            if (resolving.isEmpty()) {
                RESOLVING.remove();
            }
        }
    }

    /**
     * Identifies a page for the re-entrancy check: its source id, else its URL, else its identity.
     */
    private static String pageKey(Page page) {
        PageSource source = page.source();
        if (source != null && source.id() != null) {
            return source.id();
        }
        if (page.url() != null) {
            return page.url().toString();
        }
        return "page@" + System.identityHashCode(page);
    }

    /**
     * Returns a pre-rendered HTML navigation block for the table of contents.
     * The nav element receives an {@code aria-label} sourced from the {@code content-toc-title}
     * frontmatter key (defaults to {@code "Table of contents"}). Each list item carries a
     * {@code data-level} attribute with 0-indexed depth (h1 → 0, h2 → 1, …), matching the
     * convention used by the default theme's {@code toc.js}.
     * <p>
     * Usage in Qute templates: {@code {page.tocHtml}}
     */
    @TemplateExtension
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

    private static String markupOf(Page page) {
        PageSource source = page.source();
        return source == null ? null : source.markup();
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
     * Resolves the maximum heading tag level (1–6) to include in the TOC, in order of precedence:
     * <ol>
     * <li>the {@code content-toc-levels} frontmatter key (a heading tag level);</li>
     * <li>for AsciiDoc pages, {@code toclevels} from the {@code asciidoc-attributes} frontmatter map;</li>
     * <li>for AsciiDoc pages, {@code toclevels} from the attributes Roq parsed from the document header;</li>
     * <li>for AsciiDoc pages, a {@code :toclevels:} attribute entry in the document header;</li>
     * <li>Asciidoctor's default of 2 for AsciiDoc pages, or all six levels otherwise.</li>
     * </ol>
     * AsciiDoc {@code toclevels} counts section depth ({@code sect1} renders as {@code h2}), so it is
     * converted to a heading tag level by adding one. The result matches what Asciidoctor's own
     * {@code :toc:} output would show for the same document.
     */
    static int resolveMaxLevel(JsonObject data, String markup, Supplier<String> source) {
        Integer explicit = data == null ? null : asInteger(data.getValue(CONTENT_TOC_LEVELS));
        if (explicit != null) {
            return explicit;
        }
        if (!ASCIIDOC_MARKUP.equals(markup)) {
            return DEFAULT_MAX_LEVEL;
        }
        Integer tocLevels = asciidocTocLevels(data, source);
        return (tocLevels != null ? tocLevels : ASCIIDOC_DEFAULT_TOC_LEVELS) + 1;
    }

    /**
     * Returns the AsciiDoc {@code toclevels} value from the page's AsciiDoc attribute maps, else from a
     * {@code :toclevels:} entry in the document header, else {@code null}. The source is only read when
     * no attribute map supplies a value.
     */
    static Integer asciidocTocLevels(JsonObject data, Supplier<String> source) {
        if (data != null) {
            for (String key : ASCIIDOC_ATTRIBUTE_KEYS) {
                Integer fromAttributes = tocLevelsFrom(data.getValue(key));
                if (fromAttributes != null) {
                    return fromAttributes;
                }
            }
        }
        String text = source == null ? null : source.get();
        return text == null ? null : tocLevelsFromSource(text);
    }

    /**
     * Returns {@code toclevels} from a map of AsciiDoc attributes, which reaches the page data either as
     * a {@link JsonObject} or, when a page declares it in frontmatter, as a plain {@link Map}.
     */
    static Integer tocLevelsFrom(Object attributes) {
        if (attributes instanceof JsonObject json) {
            return asInteger(json.getValue("toclevels"));
        }
        if (attributes instanceof Map<?, ?> map) {
            return asInteger(map.get("toclevels"));
        }
        return null;
    }

    /**
     * Returns the last {@code :toclevels:} entry in the document header, else {@code null}. Only the
     * header is scanned, because an attribute entry below the first section title is body content and a
     * literal {@code :toclevels:} inside a listing block is not an attribute at all. Asciidoctor applies
     * the last assignment, so the last entry in the header wins.
     */
    static Integer tocLevelsFromSource(String source) {
        Matcher firstSection = ASCIIDOC_FIRST_SECTION.matcher(source);
        String header = firstSection.find() ? source.substring(0, firstSection.start()) : source;
        Matcher m = ASCIIDOC_TOC_LEVELS.matcher(header);
        Integer value = null;
        while (m.find()) {
            value = asInteger(m.group(1));
        }
        return value;
    }

    static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            String text = string.trim();
            // Asciidoctor's soft-set syntax marks a value a document can override: ":toclevels: 3@".
            if (text.endsWith("@")) {
                text = text.substring(0, text.length() - 1).trim();
            }
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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

    private static String escapeHtml(String text) {
        return Entities.escape(text, new Document.OutputSettings().charset("UTF-8").escapeMode(Entities.EscapeMode.base));
    }

    private static String escapeAttr(String text) {
        return escapeHtml(text).replace("\"", "&quot;");
    }

    record HeadingInfo(String id, String title, int level) {
    }
}
