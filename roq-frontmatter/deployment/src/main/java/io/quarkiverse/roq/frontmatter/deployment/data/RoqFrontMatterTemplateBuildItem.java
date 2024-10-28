package io.quarkiverse.roq.frontmatter.deployment.data;

import io.quarkiverse.roq.frontmatter.deployment.scan.RoqFrontMatterRawTemplateBuildItem;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Roq FM file with the data already processed.
 * This is meant for reading purpose.
 */
public final class RoqFrontMatterTemplateBuildItem extends MultiBuildItem {
    private final RoqFrontMatterRawTemplateBuildItem raw;
    private final RoqUrl url;
    private final JsonObject data;

    RoqFrontMatterTemplateBuildItem(RoqFrontMatterRawTemplateBuildItem raw, RoqUrl url, JsonObject data) {
        this.raw = raw;
        this.url = url;
        this.data = data;
    }

    public boolean published() {
        return raw.published();
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

    public boolean isPage() {
        return raw.isPage();
    }

    public boolean isLayout() {
        return raw.isLayout();
    }
}
