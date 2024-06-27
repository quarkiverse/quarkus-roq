package io.quarkiverse.roq.generator.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class FixedPathBuildItem extends MultiBuildItem {

    private final String path;

    public FixedPathBuildItem(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}