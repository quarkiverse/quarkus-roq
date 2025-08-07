package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq layout template.
 * The data has been inherited from parent layouts.
 * This is meant for reading purpose.
 */
public final class RoqFrontMatterLayoutTemplateBuildItem extends MultiBuildItem {
    private final RoqFrontMatterRawTemplateBuildItem raw;
    private final JsonObject data;

    RoqFrontMatterLayoutTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem raw, JsonObject data) {
        this.raw = raw;
        this.data = data;
    }

    public JsonObject data() {
        return data;
    }

    public RoqFrontMatterRawTemplateBuildItem raw() {
        return raw;
    }

}
