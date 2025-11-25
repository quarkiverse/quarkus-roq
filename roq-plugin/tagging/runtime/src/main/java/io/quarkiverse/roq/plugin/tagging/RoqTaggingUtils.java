package io.quarkiverse.roq.plugin.tagging;

import java.util.List;
import java.util.stream.Stream;

import io.quarkiverse.roq.frontmatter.runtime.RoqTemplateExtension;
import io.vertx.core.json.JsonObject;

public class RoqTaggingUtils {
    public static List<String> slugifiedTagStrings(JsonObject data) {
        return slugifiedTagStringsStream(data).toList();
    }

    static Stream<String> slugifiedTagStringsStream(JsonObject data) {
        return RoqTemplateExtension.asStrings(data.getValue("tags")).stream()
                .map(RoqTemplateExtension::slugify);
    }
}
