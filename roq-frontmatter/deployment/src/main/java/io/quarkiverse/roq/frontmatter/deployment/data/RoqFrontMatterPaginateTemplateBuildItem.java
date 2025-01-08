package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.runtime.config.ConfiguredCollection;
import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPaginateTemplateBuildItem extends MultiBuildItem {

    private final PageInfo info;
    private final ConfiguredCollection paginatedCollection;
    private final RoqUrl url;
    private final JsonObject data;

    public RoqFrontMatterPaginateTemplateBuildItem(RoqUrl url, PageInfo info, JsonObject data,
            ConfiguredCollection paginatedCollection) {
        this.info = info;
        this.paginatedCollection = paginatedCollection;
        this.url = url;
        this.data = data;
    }

    public ConfiguredCollection defaultPaginatedCollection() {
        return paginatedCollection;
    }

    public PageInfo info() {
        return info;
    }

    public RoqUrl url() {
        return url;
    }

    public JsonObject data() {
        return data;
    }
}
