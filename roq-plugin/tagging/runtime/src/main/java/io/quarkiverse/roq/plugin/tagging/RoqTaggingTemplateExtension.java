package io.quarkiverse.roq.plugin.tagging;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.Site;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonArray;

@TemplateExtension
public class RoqTaggingTemplateExtension {

    /**
     * Returns a list of all tags from the given collection, with each tag slugified.
     */
    public static List<String> allTags(RoqCollection collection) {
        return collection.stream()
                .flatMap(documentPage -> RoqTaggingUtils.slugifiedTagStringsStream(documentPage.data()))
                .toList();
    }

    /**
     * Returns a list of tags in the given collection along with the number of times
     * each tag appears. Each tag is slugified.
     */
    public static List<TagCount> tagsCount(RoqCollection collection) {
        return collection
                .stream()
                .flatMap(documentPage -> RoqTaggingUtils.slugifiedTagStringsStream(documentPage.data()))
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
     * Support for site.tags aggregation across all collections.
     * Returns a Map<String, List<Page>> where keys are tag names (slugified)
     * and values are lists of pages tagged with that tag.
     *
     * This enables this syntax in templates:
     * {#for entry in site.tags}
     * Tag: {entry.key} has {entry.value.size} pages
     * {/for}
     *
     * Or with sorting:
     * {#let tag_words=site.tags.entrySet.sort('key')}
     * {#for entry in tag_words}
     * <a href="/blog/tag/{entry.key}">{entry.key}</a> ({entry.value.size})
     * {/for}
     * {/let}
     *
     *
     * @param site the Roq site
     * @return a map of tag names to lists of pages with that tag
     */
    public static Map<String, List<Page>> tags(Site site) {
        if (site == null) {
            return Map.of();
        }

        Map<String, List<Page>> tagMap = new LinkedHashMap<>();

        // Aggregate tags from all pages across all collections
        for (Page page : site.allPages()) {
            List<String> tags = extractTags(page);
            for (String tag : tags) {
                // Slugify the tag (lowercase, replace spaces with hyphens)
                String slugified = RoqTemplateExtension.slugify(tag);
                tagMap.computeIfAbsent(slugified, k -> new ArrayList<>()).add(page);
            }
        }

        return tagMap;
    }

    /**
     * Extract tags from a page's frontmatter data.
     * Handles both List<String> and comma-separated String formats.
     * Package-private for testing.
     */
    static List<String> extractTags(Page page) {
        Object tagsObj = page.data().getValue("tags");
        if (tagsObj == null) {
            return List.of();
        }

        // Handle List format (most common)
        if (tagsObj instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .collect(Collectors.toList());
        }

        // Handle JsonArray format
        if (tagsObj instanceof JsonArray jsonArray) {
            List<String> result = new ArrayList<>();
            for (int i = 0; i < jsonArray.size(); i++) {
                Object item = jsonArray.getValue(i);
                if (item != null) {
                    result.add(item.toString());
                }
            }
            return result;
        }

        // Handle comma-separated String format
        if (tagsObj instanceof String str) {
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return List.of();
    }

}
