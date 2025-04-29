package io.quarkiverse.roq.frontmatter.runtime;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import io.quarkiverse.roq.frontmatter.runtime.model.*;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.http.impl.MimeMapping;
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
        final String text = stripHtml(page.site().pageContent(page));
        final long count = numberOfWords(text);
        return ceilDiv(count, 200);
    }

    public static String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return STRIP_HTML_PATTERN.matcher(html).replaceAll("");
    }

    public static String slugify(String text) {
        return PathUtils.slugify(text, false, false);
    }

    @SuppressWarnings("unchecked")
    public static List<String> asStrings(Object o) {
        if (o instanceof String i) {
            return Arrays.stream(i.split("[, \t;]"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        if (o instanceof JsonArray i) {
            return i.getList();
        }
        return List.of();
    }

    public static String mimeType(String fileName) {
        return MimeMapping.getMimeTypeForFilename(fileName);
    }

    private static long ceilDiv(long x, long y) {
        final long q = x / y;
        // if the signs are the same and modulo not zero, round up
        if ((x ^ y) >= 0 && (q * y != x)) {
            return q + 1;
        }
        return q;
    }
}
