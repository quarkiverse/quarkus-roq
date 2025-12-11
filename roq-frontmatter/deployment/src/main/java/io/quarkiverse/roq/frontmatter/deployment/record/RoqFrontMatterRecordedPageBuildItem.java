package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterRecordedPageBuildItem extends MultiBuildItem {
    private final String id;
    private final RoqUrl url;
    private final boolean hidden;
    private final Supplier<? extends Page> page;

    public RoqFrontMatterRecordedPageBuildItem(String id, RoqUrl url, boolean hidden, Supplier<? extends Page> page) {
        this.id = id;
        this.url = url;
        this.hidden = hidden;
        this.page = page;
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

    public Supplier<? extends Page> page() {
        return page;
    }
}
