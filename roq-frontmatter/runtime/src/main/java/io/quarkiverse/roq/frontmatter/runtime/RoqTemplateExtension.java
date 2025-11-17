package io.quarkiverse.roq.frontmatter.runtime;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
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

    public static String contentAbstract(Page page) {
        return contentAbstract(page, 75);
    }

    public static String contentAbstract(Page page, int limit) {
        return contentAbstract(page.content(), limit);
    }

    public static String contentAbstract(String htmlContent, int limit) {
        return wordLimit(stripHtml(htmlContent), limit);
    }

    public static String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return STRIP_HTML_PATTERN.matcher(html).replaceAll("");
    }

    public static String wordLimit(String text, int limit) {
        Matcher m = COUNT_WORDS.matcher(text);
        int count = 0;
        int end = -1;

        while (m.find()) {
            count++;
            if (count == limit) {
                end = m.end();
                break;
            }
        }

        if (end == -1 || end >= text.length()) {
            return text;
        } else {
            return text.substring(0, end).trim() + "...";
        }
    }

    public static String slugify(String text) {
        return PathUtils.slugify(text, false, false);
    }

    /**
     * Normalizes a Front Matter (FM) data field into a list of strings,
     * regardless of whether it's originally defined as:
     * <ul>
     * <li>A list of strings</li>
     * <li>A single string containing values separated by commas (`,`), semicolons (`;`), or tabs</li>
     * </ul>
     *
     * @param o the input object representing the FM data field
     * @return a list of normalized string values
     */
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

    public static List<DocumentPage> limit(List<DocumentPage> list, int limit) {
        return list.subList(0, Math.min(limit, list.size()));
    }

    public static List<DocumentPage> filter(List<DocumentPage> list, String key, Object value) {
        return list.stream().filter(p -> Objects.equals(p.data().getValue(key), value)).toList();
    }

    /**
     * @return future documents
     */
    public static List<DocumentPage> future(List<DocumentPage> list) {
        return list.stream().filter(d -> d.date().isAfter(ZonedDateTime.now())).toList();
    }

    /**
     * @return past documents
     */
    public static List<DocumentPage> past(List<DocumentPage> list) {
        return list.stream().filter(d -> d.date().isBefore(ZonedDateTime.now())).toList();
    }

    public static List<DocumentPage> randomise(List<DocumentPage> l) {
        final ArrayList<DocumentPage> list = new ArrayList<>(l);
        Collections.shuffle(list);
        return list;
    }

    public static List<DocumentPage> sortBy(List<DocumentPage> list, String key, boolean reverse) {
        Comparator<DocumentPage> comparing = Comparator.comparing(p -> p.data().getString(key));
        if (reverse) {
            comparing = comparing.reversed();
        }
        return list.stream().sorted(comparing).toList();
    }

    public static List<DocumentPage> sortByDate(List<DocumentPage> list, boolean reverse) {
        Comparator<DocumentPage> comparing = Comparator.comparing(Page::date);
        if (reverse) {
            comparing = comparing.reversed();
        }
        return list.stream().sorted(comparing).toList();
    }

    public static List<DocumentPage> reverse(List<DocumentPage> l) {
        List<DocumentPage> shallowCopy = l.subList(0, l.size());
        Collections.reverse(shallowCopy);
        return shallowCopy;
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
