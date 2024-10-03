package io.quarkiverse.roq.frontmatter.deployment.publish;

import io.quarkiverse.roq.frontmatter.runtime.model.PageInfo;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishPageBuildItem extends MultiBuildItem {
    private final String link;
    private final PageInfo info;
    private final JsonObject data;
    private final Paginator paginator;

    public RoqFrontMatterPublishPageBuildItem(String link, PageInfo info, JsonObject data, Paginator paginator) {
        this.link = link;
        this.info = info;
        this.data = data;
        this.paginator = paginator;
    }

    public String link() {
        return link;
    }

    public PageInfo info() {
        return info;
    }

    public JsonObject data() {
        return data;
    }

    public Paginator paginator() {
        return paginator;
    }
}
