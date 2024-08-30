package io.quarkiverse.roq.generator.deployment.items;

import io.quarkus.builder.item.MultiBuildItem;

public final class SelectedPathBuildItem extends MultiBuildItem {
    private final String path;

    public SelectedPathBuildItem(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}
