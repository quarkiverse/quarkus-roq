package io.quarkiverse.roq.frontmatter.deployment.items.record;

import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, String> pageIdsByPath;
    private final String pageIdsByPathResource;

    /**
     * @param pageIdsByPath the routed page ids by resource path
     * @param pageIdsByPathResource the classpath resource containing the serialized pageIdsByPath map
     */
    public RoqFrontMatterOutputBuildItem(Map<String, String> pageIdsByPath, String pageIdsByPathResource) {
        this.pageIdsByPath = pageIdsByPath;
        this.pageIdsByPathResource = pageIdsByPathResource;
    }

    /**
     * The routed page ids by resource path, use {@link io.quarkiverse.roq.frontmatter.runtime.model.Site#page(String)} to
     * resolve the pages at runtime.
     */
    public Map<String, String> pageIdsByPath() {
        return pageIdsByPath;
    }

    /**
     * The classpath resource path for the serialized pageIdsByPath map.
     */
    public String pageIdsByPathResource() {
        return pageIdsByPathResource;
    }

}
