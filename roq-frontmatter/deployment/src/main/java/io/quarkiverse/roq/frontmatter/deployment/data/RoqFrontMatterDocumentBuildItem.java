package io.quarkiverse.roq.frontmatter.deployment.data;

import java.util.List;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * This is NOT meant to be produced by Roq extensions.
 * This is meant to process the data and then publish
 */
public final class RoqFrontMatterDocumentBuildItem extends MultiBuildItem {
    private final RoqFrontMatterPageTemplateBuildItem template;

    RoqFrontMatterDocumentBuildItem(RoqFrontMatterPageTemplateBuildItem template) {
        this.template = template;
    }

    public RoqFrontMatterPageTemplateBuildItem template() {
        return template;
    }

    public ConfiguredCollection collection() {
        return template.raw().collection();
    }

    public RoqUrl url() {
        return template.url();
    }

    public JsonObject data() {
        return template.data();
    }

    public boolean isPage() {
        return template.raw().isPage();
    }

    public static ConfiguredCollection getCollection(List<RoqFrontMatterDocumentBuildItem> items) {
        return items.isEmpty() ? null : items.iterator().next().collection();
    }
}
