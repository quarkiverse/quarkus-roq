package io.quarkiverse.roq.plugin.tagging;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import java.util.List;
import java.util.stream.Stream;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqCollection;
import io.quarkus.qute.TemplateExtension;
import io.vertx.core.json.JsonObject;

@TemplateExtension
public class RoqTaggingQuteExtension {

    public List<String> allTags(RoqCollection collection) {
        return collection.stream()
                .flatMap(documentPage -> slugifiedTagStringsStream(documentPage.data()))
                .toList();
    }

    public static List<String> slugifiedTagStrings(JsonObject data) {
        return slugifiedTagStringsStream(data).toList();
    }

    public static List<TagCount> tagsCount(RoqCollection collection) {
        return collection
                .stream()
                .flatMap(documentPage -> slugifiedTagStringsStream(documentPage.data()))
                .collect(groupingBy(tag -> tag, counting())).entrySet()
                .stream().map(entry -> new TagCount(entry.getKey(), String.valueOf(entry.getValue())))
                .toList();
    }

    private static Stream<String> slugifiedTagStringsStream(JsonObject data) {
        return RoqTemplateExtension.asStrings(data.getValue("tags")).stream()
                .map(RoqTemplateExtension::slugify);
    }

    public record TagCount(String name, String count) {
    }

}
