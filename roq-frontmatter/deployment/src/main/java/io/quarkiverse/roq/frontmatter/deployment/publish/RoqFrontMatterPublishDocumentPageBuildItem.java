package io.quarkiverse.roq.frontmatter.deployment.publish;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishDocumentPageBuildItem extends MultiBuildItem {
    private final RoqUrl url;
    private final PageSource source;
    private final ConfiguredCollection collection;
    private final JsonObject data;

    public RoqFrontMatterPublishDocumentPageBuildItem(RoqUrl url, PageSource source, ConfiguredCollection collection,
            JsonObject data) {
        this.url = url;
        this.source = source;
        this.collection = collection;
        this.data = data;
    }

    public ConfiguredCollection collection() {
        return collection;
    }

    public RoqUrl url() {
        return url;
    }

    public PageSource source() {
        return source;
    }

    public JsonObject data() {
        return data;
    }
}
