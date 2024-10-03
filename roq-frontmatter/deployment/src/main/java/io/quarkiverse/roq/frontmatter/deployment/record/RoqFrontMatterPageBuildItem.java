package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterPageBuildItem extends MultiBuildItem {
    private final String id;
    private final RoqUrl url;
    private final Supplier<? extends Page> page;

    public RoqFrontMatterPageBuildItem(String id, RoqUrl url, Supplier<? extends Page> page) {
        this.id = id;
        this.url = url;
        this.page = page;
    }

    public RoqUrl url() {
        return url;
    }

    public String id() {
        return id;
    }

    public Supplier<? extends Page> page() {
        return page;
    }
}
