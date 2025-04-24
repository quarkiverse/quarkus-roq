package io.quarkiverse.roq.generator.deployment;

import java.util.Map;

import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.StaticFile;
import io.quarkus.builder.item.SimpleBuildItem;

public final class BuildSelectionBuildItem extends SimpleBuildItem {
    private final Map<String, StaticFile> staticFiles;
    private final RoqSelection selectedPaths;

    public BuildSelectionBuildItem(Map<String, StaticFile> staticFiles, RoqSelection selectedPaths) {
        this.staticFiles = staticFiles;
        this.selectedPaths = selectedPaths;
    }

    public Map<String, StaticFile> staticFiles() {
        return staticFiles;
    }

    public RoqSelection selectedPaths() {
        return selectedPaths;
    }
}
