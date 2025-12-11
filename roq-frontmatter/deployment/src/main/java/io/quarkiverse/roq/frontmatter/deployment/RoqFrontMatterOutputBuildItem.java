package io.quarkiverse.roq.frontmatter.deployment;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.roq.frontmatter.runtime.model.Page;
import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqFrontMatterOutputBuildItem extends SimpleBuildItem {

    private final Map<String, Supplier<? extends Page>> allPagesByPath;

    public RoqFrontMatterOutputBuildItem(Map<String, Supplier<? extends Page>> allPagesByPath) {
        this.allPagesByPath = allPagesByPath;
    }

    public Map<String, Supplier<? extends Page>> allPagesByPath() {
        return allPagesByPath;
    }

}