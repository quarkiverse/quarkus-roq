package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkiverse.roq.frontmatter.runtime.model.RoqUrl;
import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterRecordedNormalPageBuildItem extends MultiBuildItem {
    private final String id;
    private final RoqUrl url;
    private final Supplier<NormalPage> page;

    public RoqFrontMatterRecordedNormalPageBuildItem(String id, RoqUrl url, Supplier<NormalPage> page) {
        this.id = id;
        this.url = url;
        this.page = page;
    }

    public String id() {
        return id;
    }

    public RoqUrl url() {
        return url;
    }

    public Supplier<NormalPage> page() {
        return page;
    }
}
