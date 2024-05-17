package io.quarkiverse.statiq.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class StatiqFixedPathBuildItem extends MultiBuildItem {

    private final String path;

    public StatiqFixedPathBuildItem(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}