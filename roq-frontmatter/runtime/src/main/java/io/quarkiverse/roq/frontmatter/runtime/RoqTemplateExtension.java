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
import io.vertx.core.json.JsonObject;

@TemplateExtension
public class RoqTemplateExtension {

    private static final int QUTE_FALLBACK_PRIORITY = -2;

    private static final Pattern COUNT_WORDS = Pattern.compile("\\b\\w+\\b");
    private static final Pattern STRIP_HTML_PATTERN = Pattern.compile("<[^>]*>");

    /**
     * Returns the number of words in the given text.<br>
     * Example: "{'hello world'.numberOfWords}" → 2.
     */
    public static long numberOfWords(String text) {
        return COUNT_WORDS.matcher(text).results().count();
    }

    /**
     * Returns the collection for the given key.<br>
     * Example: "{site.collections.posts}".
     */
    @TemplateExtension(matchName = "*", priority = QUTE_FALLBACK_PRIORITY)
    public static RoqCollection collection(RoqCollections collections, String key) {
        return collections.get(key);
    }

    /**
     * Returns the estimated reading time (in minutes) for the given page based on the page content.<br>
     * Example: "{page.readTime}" → 4.
     */
    public static Long readTime(Page page) {
        final String text = stripHtml(page.site().pageContent(page));
        final long count = numberOfWords(text);
        return ceilDiv(count, 200);
    }

    /**
     * Returns the content abstract for the given page (75 first words), adds "..." if truncated.<br>
     * Example: "{page.contentAbstract}".
     */
    public static String contentAbstract(Page page) {
        return contentAbstract(page, 75);
    }

    /**
     * Returns the content abstract for the given page limited to the given limit in words, adds "..." if truncated.<br>
     * Example: "{page.contentAbstract(10)}".
     */
    public static String contentAbstract(Page page, int limit) {
        return contentAbstract(page.content(), limit);
    }

    /**
     * Returns the content abstract for the given html content limited to the given limit in words, adds "..." if truncated.<br>
     * Example: "{'<div>Hello World</div>'.contentAbstract(10)}".
     */
    public static String contentAbstract(String htmlContent, int limit) {
        return wordLimit(stripHtml(htmlContent), limit);
    }

    /**
     * Returns the text part of this string by stripping all html tags.<br>
     * Example: "{'<div>Hello World</div>'.stripHtml}" → "Hello World".
     */
    public static String stripHtml(String html) {
        if (html == null) {
            return null;
        }
        return STRIP_HTML_PATTERN.matcher(html).replaceAll("");
    }

    /**
     * Returns the same text with a limit on the number of words, adds "..." if truncated.<br>
     * Example: "{'Hello World'.wordLimit(1)}" → "Hello...".
     */
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

    /**
     * Returns the slugified version of the given text.<br>
     * Example: "{'Hello World'.slugify}" → "Hello-World".
     */
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

    /**
     * Returns the Mime Type for the given file name based on the extension.<br>
     * Example: "{'foo.pdf'.mimeType}" → "application/pdf".
     */
    public static String mimeType(String fileName) {
        return MimeMapping.getMimeTypeForFilename(fileName);
    }

    /**
     * Returns a list of JsonObject. All items must be Json objects.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<JsonObject> asJsonObjects(JsonArray jsonArray) {
        return jsonArray.stream().map(item -> {
            if (item instanceof JsonObject o) {
                return o;
            } else {
                throw new RuntimeException(
                        "asJsonObjects should only be called if all items are instance of JsonObject (not: %s)".formatted(item));
            }
        }).toList();
    }

    /**
     * Returns a new list with the elements of the given list in random order.
     */
    public static <T> List<T> randomise(List<T> l) {
        final ArrayList<T> list = new ArrayList<>(l);
        Collections.shuffle(list);
        return list;
    }

    /**
     * Returns a new list with all the documents that matches the given value for the key (in the FM data).<br>
     * Example: "{list.filter('author', 'john')}" → Only the documents where the author is john.
     */
    public static List<DocumentPage> filter(List<DocumentPage> list, String key, Object value) {
        return list.stream().filter(p -> Objects.equals(p.data().getValue(key), value)).toList();
    }

    /**
     * Returns a new list containing only the documents dated in the future.<br>
     * This only works when future is enabled on the collection.
     */
    public static List<DocumentPage> future(List<DocumentPage> list) {
        return list.stream().filter(d -> d.date().isAfter(ZonedDateTime.now())).toList();
    }

    /**
     * Returns a new list containing only the documents dated in the past.
     */
    public static List<DocumentPage> past(List<DocumentPage> list) {
        return list.stream().filter(d -> d.date().isBefore(ZonedDateTime.now())).toList();
    }

    /**
     * Returns a new list with all documents sorted by the given front-matter field
     * (String comparison). Sorting can be reversed.<br>
     * Example: "{list.sortBy('author', true)}" → sorts by the "author" field in descending order.
     */
    public static List<DocumentPage> sortBy(List<DocumentPage> list, String key, boolean reverse) {
        Comparator<DocumentPage> comparing = Comparator.comparing(p -> p.data().getString(key));
        if (reverse) {
            comparing = comparing.reversed();
        }
        return list.stream().sorted(comparing).toList();
    }

    /**
     * Returns a new list with all documents sorted by their date.
     * Sorting can be reversed using the {@code reverse} flag.<br>
     * Example: {@code list.sortByDate(true)} → sorts by date in descending order.
     */
    public static List<DocumentPage> sortByDate(List<DocumentPage> list, boolean reverse) {
        Comparator<DocumentPage> comparing = Comparator.comparing(Page::date);
        if (reverse) {
            comparing = comparing.reversed();
        }
        return list.stream().sorted(comparing).toList();
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
