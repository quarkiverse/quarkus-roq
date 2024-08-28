package io.quarkiverse.roq.frontmatter.deployment.items;

import java.util.Map;

import io.quarkiverse.roq.frontmatter.runtime.Page;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, Page> pages;

    public RoqFrontMatterOutputBuildItem(Map<String, Page> pages) {
        this.pages = pages;
    }

    public Map<String, Page> pages() {
        return pages;
    }
}
