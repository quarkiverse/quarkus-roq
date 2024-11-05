package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterQuteMarkupBuildItem extends MultiBuildItem {
    private final Set<String> extensions;
    private final QuteMarkupSection markupSection;

    public RoqFrontMatterQuteMarkupBuildItem(Set<String> extensions, QuteMarkupSection markupSection) {
        this.extensions = extensions;
        this.markupSection = markupSection;
    }

    public Set<String> extensions() {
        return extensions;
    }

    public QuteMarkupSection markupSection() {
        return markupSection;
    }

    public static Map<String, QuteMarkupSection> markups(List<RoqFrontMatterQuteMarkupBuildItem> list) {
        Map<String, QuteMarkupSection> markups = new HashMap<>();
        for (RoqFrontMatterQuteMarkupBuildItem item : list) {
            for (String extension : item.extensions()) {
                markups.put(extension, item.markupSection());
            }
        }
        return markups;
    }

    public record QuteMarkupSection(String open, String close) {

        public String apply(String content) {
            return open + "\n" + content.strip() + "\n" + close;
        }

        public static Function<String, String> find(Map<String, QuteMarkupSection> markups, String fileName,
                Function<String, String> defaultFunction) {
            final String extension = PathUtils.getExtension(fileName);
            if (extension == null) {
                return defaultFunction;
            }
            if (!markups.containsKey(extension)) {
                return defaultFunction;
            }
            return markups.get(extension)::apply;
        }
    }
}
