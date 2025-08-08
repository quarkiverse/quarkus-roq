package io.quarkiverse.roq.frontmatter.deployment.publish;

import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.Paginator;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

public final class RoqFrontMatterPublishNormalPageBuildItem extends MultiBuildItem {
    private final RoqUrl url;
    private final PageSource source;
    private final JsonObject data;
    private final Paginator paginator;

    public RoqFrontMatterPublishNormalPageBuildItem(RoqUrl url, PageSource source, JsonObject data, Paginator paginator) {
        this.url = url;
        this.source = source;
        this.data = data;
        this.paginator = paginator;
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

    public Paginator paginator() {
        return paginator;
    }
}
