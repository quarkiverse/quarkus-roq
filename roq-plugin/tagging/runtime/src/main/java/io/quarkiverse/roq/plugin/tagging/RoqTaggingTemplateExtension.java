package io.quarkiverse.roq.plugin.tagging;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.TemplateExtension;

@TemplateExtension
public class RoqTaggingTemplateExtension {

    /**
     * Returns a list of all tags from the given collection, with each tag slugified.
     */
    public static List<String> allTags(RoqCollection collection) {
        boolean lowercase = isLowercase();
        return collection.stream()
                .flatMap(documentPage -> RoqTaggingUtils.slugifiedTagStringsStream(documentPage.data(), lowercase))
                .toList();
    }

    /**
     * Returns a list of tags in the given collection along with the number of times
     * each tag appears. Each tag is slugified.
     */
    public static List<TagCount> tagsCount(RoqCollection collection) {
        boolean lowercase = isLowercase();
        return collection
                .stream()
                .flatMap(documentPage -> RoqTaggingUtils.slugifiedTagStringsStream(documentPage.data(), lowercase))
                .collect(groupingBy(tag -> tag, counting())).entrySet()
                .stream().map(entry -> new TagCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * Represents a tag and its count.
     *
     * @param name the tag name
     * @param count the count of the tag
     */
    public record TagCount(String name, Long count) {
    }

    /**
     * Support for site.tags aggregation across all collections, allowing site.tags to be used in templates.
     * Returns a Map<String, List<Page>> where keys are tag names (slugified)
     * and values are lists of pages tagged with that tag.
     *
     * @param site the Roq site
     * @return a map of tag names to lists of pages with that tag
     */
    public static Map<String, List<Page>> tags(Site site) {
        if (site == null) {
            return Map.of();
        }

        boolean lowercase = isLowercase();
        Map<String, List<Page>> tagMap = new LinkedHashMap<>();

        for (Page page : site.allPages()) {
            for (String tag : RoqTaggingUtils.slugifiedTagStrings(page.data(), lowercase)) {
                tagMap.computeIfAbsent(tag, k -> new ArrayList<>()).add(page);
            }
        }

        return tagMap;
    }

    private static boolean isLowercase() {
        return ConfigProvider.getConfig()
                .getOptionalValue("quarkus.roq.tagging.lowercase", Boolean.class)
                .orElse(false);
    }

}
