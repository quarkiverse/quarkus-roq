package io.quarkiverse.roq.frontmatter.deployment;

import io.quarkiverse.roq.frontmatter.runtime.model.RootUrl;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterRootUrlBuildItem extends SimpleBuildItem {

    private final RootUrl rootUrl;

    public RoqFrontMatterRootUrlBuildItem(RootUrl rootUrl) {
        this.rootUrl = rootUrl;
    }

    public RootUrl rootUrl() {
        return rootUrl;
    }
}
