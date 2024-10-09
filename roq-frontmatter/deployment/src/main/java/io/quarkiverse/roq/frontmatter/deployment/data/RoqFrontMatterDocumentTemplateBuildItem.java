package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishDocumentPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.publish.RoqFrontMatterPublishPageBuildItem;
import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
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
    private final String collection;
    private final JsonObject data;

    RoqFrontMatterDocumentTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem raw, RoqUrl url, String collection,
            JsonObject data) {
        this.raw = raw;
        this.url = url;
        this.collection = collection;
        this.data = data;
    }

    public RoqFrontMatterRawTemplateBuildItem raw() {
        return raw;
    }

    public String collection() {
        return collection;
    }

    public RoqUrl url() {
        return url;
    }

    public JsonObject data() {
        return data;
    }
}
