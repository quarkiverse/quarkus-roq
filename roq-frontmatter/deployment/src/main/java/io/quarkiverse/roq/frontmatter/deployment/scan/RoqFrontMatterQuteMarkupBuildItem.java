package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.quarkiverse.roq.frontmatter.runtime.WrapperFilter;
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

    public static Map<String, WrapperFilter> toWrapperFilters(List<RoqFrontMatterQuteMarkupBuildItem> list) {
        Map<String, WrapperFilter> markups = new HashMap<>();
        for (RoqFrontMatterQuteMarkupBuildItem item : list) {
            for (String extension : item.extensions()) {
                markups.put(extension, new WrapperFilter(item.markupSection.open + "\n", "\n" + item.markupSection.close));
            }
        }
        return markups;
    }

    public record QuteMarkupSection(String open, String close) {

        public static WrapperFilter find(Map<String, WrapperFilter> markups, String fileName,
                WrapperFilter defaultFilter) {
            final String extension = PathUtils.getExtension(fileName);
            if (extension == null) {
                return defaultFilter;
            }
            if (!markups.containsKey(extension)) {
                return defaultFilter;
            }
            return markups.get(extension);
        }
    }
}
