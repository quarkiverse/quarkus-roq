package io.quarkiverse.roq.frontmatter.deployment.record;

import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.NormalPage;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterRecordedSiteIndexBuildItem extends SimpleBuildItem {
    private final Supplier<NormalPage> page;

    public RoqFrontMatterRecordedSiteIndexBuildItem(Supplier<NormalPage> page) {
        this.page = page;
    }

    public Supplier<NormalPage> page() {
        return page;
    }
}
