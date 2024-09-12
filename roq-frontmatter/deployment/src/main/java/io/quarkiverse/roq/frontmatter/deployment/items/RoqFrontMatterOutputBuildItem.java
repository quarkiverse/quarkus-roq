package io.quarkiverse.roq.frontmatter.deployment.items;

import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, String> paths;

    public RoqFrontMatterOutputBuildItem(Map<String, String> paths) {
        this.paths = paths;
    }

    public Map<String, String> paths() {
        return paths;
    }
}
