package io.quarkiverse.roq.frontmatter.runtime;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.quarkus.qute.Results;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class RoqTemplateExtension {
    private static final Set<String> PAGE_METHOD_NAMES = Arrays.stream(Page.class.getMethods()).sequential()
            .map(Method::getName)
            .collect(Collectors.toSet());
    private static final Pattern COUNT_WORDS = Pattern.compile("\\b\\w+\\b");
    public static final Results.NotFound NOT_PAGE_DATA = Results.NotFound.from("Not Page Data");

    public static long numberOfWords(String text) {
        return COUNT_WORDS.matcher(text).results().count();
    }

    @TemplateExtension(matchName = "*")
    public static Object data(Page page, String key) {
        if (PAGE_METHOD_NAMES.contains(key)) {
            return NOT_PAGE_DATA;
        }
        return page.data(key);
    }

    @TemplateExtension(matchName = "*")
    public static RoqCollection collection(RoqCollections collections, String key) {
        return collections.get(key);
    }

    public static Object readTime(Page page) {
        final long count = numberOfWords(page.rawContent());
        return Math.round((float) count / 200);
    }

    public static RoqUrl toUrl(Object url) {
        return new RoqUrl(url.toString());
    }
}
