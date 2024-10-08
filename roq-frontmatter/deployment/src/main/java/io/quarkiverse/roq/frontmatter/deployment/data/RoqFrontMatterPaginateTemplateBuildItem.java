package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPaginateTemplateBuildItem extends MultiBuildItem {

    private final RoqFrontMatterRawTemplateBuildItem raw;
    private final String paginatedCollection;
    private final RoqUrl url;
    private final JsonObject data;

    public RoqFrontMatterPaginateTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem raw, String paginatedCollection,
            RoqUrl url, JsonObject data) {
        this.raw = raw;
        this.paginatedCollection = paginatedCollection;
        this.url = url;
        this.data = data;
    }

    public String defaultPaginatedCollection() {
        return paginatedCollection;
    }

    public RoqFrontMatterRawTemplateBuildItem raw() {
        return raw;
    }

    public RoqUrl url() {
        return url;
    }

    public JsonObject data() {
        return data;
    }
}
