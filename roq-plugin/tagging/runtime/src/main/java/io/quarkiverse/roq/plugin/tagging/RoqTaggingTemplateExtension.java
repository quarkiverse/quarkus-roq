package io.quarkiverse.roq.plugin.tagging;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkus.qute.TemplateExtension;

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

}
