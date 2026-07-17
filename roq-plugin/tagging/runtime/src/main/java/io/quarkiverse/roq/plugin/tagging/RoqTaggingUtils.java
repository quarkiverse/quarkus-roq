package io.quarkiverse.roq.plugin.tagging;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import io.quarkiverse.roq.frontmatter.runtime.RoqFrontMatterKeys;
import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.vertx.core.json.JsonObject;

public class RoqTaggingUtils {
    public static List<String> slugifiedTagStrings(JsonObject data, boolean lowercase) {
        return slugifiedTagStringsStream(data, lowercase).toList();
    }

    static Stream<String> slugifiedTagStringsStream(JsonObject data, boolean lowercase) {
        Stream<String> tagStream = RoqTemplateExtension.asStrings(data.getValue(RoqFrontMatterKeys.TAGS)).stream()
                .map(RoqTemplateExtension::slugify);

        // Apply lowercase transformation if configured
        if (lowercase) {
            tagStream = tagStream.map(tag -> tag.toLowerCase(Locale.ROOT));
        }

        return tagStream;
    }
}
