package io.quarkiverse.roq.frontmatter.deployment.publish;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishDocumentPageBuildItem extends MultiBuildItem {
    private final RoqUrl url;
    private final PageInfo info;
    private final ConfiguredCollection collection;
    private final JsonObject data;

    public RoqFrontMatterPublishDocumentPageBuildItem(RoqUrl url, PageInfo info, ConfiguredCollection collection,
            JsonObject data) {
        this.url = url;
        this.info = info;
        this.collection = collection;
        this.data = data;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public RoqUrl url() {
        return url;
    }

    public PageInfo info() {
        return info;
    }

    public JsonObject data() {
        return data;
    }
}
