package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, String> pageIdsByPath;

    /**
     * @param pageIdsByPath the routed page ids by resource path
     */
    public RoqFrontMatterOutputBuildItem(Map<String, String> pageIdsByPath) {
        this.pageIdsByPath = pageIdsByPath;
    }

    /**
     * The routed page ids by resource path, use {@link io.quarkiverse.roq.frontmatter.runtime.model.Site#page(String)} to
     * resolve the pages at runtime.
     */
    public Map<String, String> pageIdsByPath() {
        return pageIdsByPath;
    }

}
