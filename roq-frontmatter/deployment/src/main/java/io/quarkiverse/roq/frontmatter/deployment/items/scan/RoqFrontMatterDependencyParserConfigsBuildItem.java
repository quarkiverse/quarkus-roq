package io.quarkiverse.roq.frontmatter.deployment.items.scan;

import java.nio.file.Path;
import java.util.Map;

import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.qute.ParserConfig;

/**
 * Holds the pre-scanned {@link ParserConfig} for each dependency template root,
 * derived from {@code .qute} config files found in dependency JARs.
 */
public final class RoqFrontMatterDependencyParserConfigsBuildItem extends SimpleBuildItem {

    private final Map<Path, ParserConfig> configs;

    public RoqFrontMatterDependencyParserConfigsBuildItem(Map<Path, ParserConfig> configs) {
        this.configs = Map.copyOf(configs);
    }

    public Map<Path, ParserConfig> configs() {
        return configs;
    }
}
