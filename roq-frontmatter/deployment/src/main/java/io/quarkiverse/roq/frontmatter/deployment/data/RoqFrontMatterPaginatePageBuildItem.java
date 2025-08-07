package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPaginatePageBuildItem extends MultiBuildItem {

    private final PageSource source;
    private final ConfiguredCollection paginatedCollection;
    private final RoqUrl url;
    private final JsonObject data;

    public RoqFrontMatterPaginatePageBuildItem(RoqUrl url, PageSource source, JsonObject data,
            ConfiguredCollection paginatedCollection) {
        this.source = source;
        this.paginatedCollection = paginatedCollection;
        this.url = url;
        this.data = data;
    }

    public ConfiguredCollection defaultPaginatedCollection() {
        return paginatedCollection;
    }

    public PageSource source() {
        return source;
    }

    public RoqUrl url() {
        return url;
    }

    public JsonObject data() {
        return data;
    }
}
