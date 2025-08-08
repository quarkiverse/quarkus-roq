package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.model.PageSource;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a page templates.
 * The data has been inherited from parent layouts.
 * This is meant for reading purpose.
 */
public final class RoqFrontMatterPageTemplateBuildItem extends MultiBuildItem {
    private final RoqFrontMatterRawTemplateBuildItem raw;
    private final JsonObject data;
    private final RoqUrl url;
    private final PageSource source;

    RoqFrontMatterPageTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem raw, JsonObject data, PageSource source,
            RoqUrl url) {
        this.raw = raw;
        this.data = data;
        this.url = url;
        this.source = source;
    }

    public RoqFrontMatterRawTemplateBuildItem raw() {
        return raw;
    }

    public JsonObject data() {
        return data;
    }

    public PageSource source() {
        return source;
    }

    public RoqUrl url() {
        return url;
    }

}
