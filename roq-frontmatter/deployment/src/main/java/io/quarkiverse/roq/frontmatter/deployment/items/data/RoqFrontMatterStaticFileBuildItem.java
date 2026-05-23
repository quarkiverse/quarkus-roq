package io.quarkiverse.roq.frontmatter.deployment.items.data;

import java.nio.file.Path;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFrontMatterStaticFileBuildItem extends MultiBuildItem {
    private final String link;
    private final Path filePath;
    private final byte[] content;

    public RoqFrontMatterStaticFileBuildItem(String link, Path filePath) {
        this.link = link;
        this.filePath = filePath;
        this.content = null;
    }

    public RoqFrontMatterStaticFileBuildItem(String link, byte[] content) {
        this.link = link;
        this.filePath = null;
        this.content = content;
    }

    public String link() {
        return link;
    }

    public Path filePath() {
        return filePath;
    }

    public byte[] content() {
        return content;
    }

    public boolean isFile() {
        return filePath != null;
    }
}
