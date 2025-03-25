package io.quarkiverse.roq.frontmatter.runtime;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.Arc;
import io.quarkus.qute.Engine;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonArray;

@TemplateExtension
public class RoqTemplateExtension {

    private static final int QUTE_FALLBACK_PRIORITY = -2;

    private static final Pattern COUNT_WORDS = Pattern.compile("\\b\\w+\\b");
    private static final Pattern STRIP_HTML_PATTERN = Pattern.compile("<[^>]*>");

    public static long numberOfWords(String text) {
        return COUNT_WORDS.matcher(text).results().count();
    }

    @TemplateExtension(matchName = "*", priority = QUTE_FALLBACK_PRIORITY)
    public static RoqCollection collection(RoqCollections collections, String key) {
        return collections.get(key);
    }

    public static Object readTime(Page page) {
        final long count = numberOfWords(page.rawContent());
        return Math.round((float) count / 200);
    }

    /**
     * Renders the inner content of the given {@link Page} using the Qute template engine.
     * <p>
     * This method parses the raw content of the page and renders it with a context
     * that includes the page itself and the site configuration.
     * </p>
     * <strong>Warning:</strong> Do not call this method from within a template that
     * is already rendering the page content, as it will result in a stack overflow.
     *
     * @param page the {@link Page} to render
     * @return the rendered content of the page
     */
    public static String content(Page page) {
        final Engine engine = Arc.container().instance(Engine.class).get();
        return engine.parse(page.rawContent()).render(Map.of(
                "page", page,
                "site", Arc.container().instance(Site.class).get()));
    }

    public static String stripHtml(String html) {
        return STRIP_HTML_PATTERN.matcher(html).replaceAll("");
    }

    public static String slugify(String text) {
        return PathUtils.slugify(text, false, false);
    }

    @SuppressWarnings("unchecked")
    public static List<String> asStrings(Object o) {
        if (o instanceof String i) {
            return List.of((i).split("\\h*,\\h*|\\h{2,}"));
        }
        if (o instanceof JsonArray i) {
            return i.getList();
        }
        return List.of();
    }
}
