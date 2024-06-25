package io.quarkiverse.roq.deployment;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqFixedPathBuildItem extends MultiBuildItem {

    private final String path;

    public RoqFixedPathBuildItem(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }
}