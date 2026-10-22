package io.quarkiverse.roq.frontmatter.deployment.items.record;

import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterRecordedPageBuildItem extends MultiBuildItem {
    private final String id;
    private final RoqUrl url;
    private final boolean hidden;

    public RoqFrontMatterRecordedPageBuildItem(String id, RoqUrl url, boolean hidden) {
        this.id = id;
        this.url = url;
        this.hidden = hidden;
    }

    public RoqUrl url() {
        return url;
    }

    public boolean hidden() {
        return hidden;
    }

    public String id() {
        return id;
    }
}
