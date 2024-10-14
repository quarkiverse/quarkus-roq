package io.quarkiverse.roq.frontmatter.deployment.scan;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterStaticFileBuildItem extends MultiBuildItem {
    private final String link;
    private final Path filePath;

    public RoqFrontMatterStaticFileBuildItem(String link, Path filePath) {
        this.link = link;
        this.filePath = filePath;
    }

    public String link() {
        return link;
    }

    public Path filePath() {
        return filePath;
    }
}
