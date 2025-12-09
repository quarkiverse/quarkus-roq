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

    /**
     * The source file path (absolute path to the source file on disk).
     * Null for non-file-based paths (e.g., dynamic endpoints).
     */
    private final String sourceFilePath;

    public SelectedPathBuildItem(String path, String outputPath) {
        this(path, outputPath, null);
    }

    public SelectedPathBuildItem(String path, String outputPath, String sourceFilePath) {
        this.path = path;
        this.outputPath = outputPath == null ? "" : outputPath;
        this.sourceFilePath = sourceFilePath;
    }

    public String path() {
        return path;
    }

    public String outputPath() {
        return outputPath;
    }

    public String sourceFilePath() {
        return sourceFilePath;
    }
}
