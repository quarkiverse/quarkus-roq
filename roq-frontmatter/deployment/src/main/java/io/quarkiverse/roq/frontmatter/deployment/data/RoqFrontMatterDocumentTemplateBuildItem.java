package io.quarkiverse.roq.frontmatter.deployment.data;

import java.util.List;

import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * This is NOT meant to be produced by Roq extensions.
 * This is meant to process the data and then publish
 * {@link RoqFrontMatterPublishDocumentPageBuildItem}, {@link RoqFrontMatterPublishPageBuildItem} and
 * {@link io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDerivedCollectionBuildItem}
 */
public final class RoqFrontMatterDocumentTemplateBuildItem extends MultiBuildItem {
    private final RoqFrontMatterRawTemplateBuildItem raw;
    private final RoqUrl url;
    private final ConfiguredCollection collection;
    private final JsonObject data;

    RoqFrontMatterDocumentTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem raw, RoqUrl url, ConfiguredCollection collection,
            JsonObject data) {
        this.raw = raw;
        this.url = url;
        this.collection = collection;
        this.data = data;
    }

    public RoqFrontMatterRawTemplateBuildItem raw() {
        return raw;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public RoqUrl url() {
        return url;
    }

    public JsonObject data() {
        return data;
    }

    public boolean isPage() {
        return raw.isPage();
    }

    public static ConfiguredCollection getCollection(List<RoqFrontMatterDocumentTemplateBuildItem> items) {
        return items.isEmpty() ? null : items.iterator().next().collection();
    }
}
