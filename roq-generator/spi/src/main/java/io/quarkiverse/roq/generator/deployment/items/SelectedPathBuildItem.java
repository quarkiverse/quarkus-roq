package io.quarkiverse.roq.generator.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class SelectedPathBuildItem extends MultiBuildItem {

    /**
     * The path to fetch content from starting with / (without the root-path).
     */
    private final String path;

    /**
     * The output path to generate.
     * If empty or null it will be auto-generated from the path
     */
    private final String outputPath;

    public SelectedPathBuildItem(String path, String outputPath) {
        this.path = path;
        this.outputPath = outputPath == null ? "" : outputPath;
    }

    public String path() {
        return path;
    }

    public String outputPath() {
        return outputPath;
    }
}
