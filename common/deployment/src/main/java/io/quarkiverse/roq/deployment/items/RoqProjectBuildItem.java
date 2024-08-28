package io.quarkiverse.roq.deployment.items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.util.ClassPathUtils;

public final class RoqProjectBuildItem extends SimpleBuildItem {
    private final String resourceSiteDir;

    public RoqProjectBuildItem(String resourceSiteDir) {
        this.resourceSiteDir = resourceSiteDir;
    }

    public boolean isActive() {
        return resourceSiteDir != null;
    }

    public void consumePathFromSite(String resource, Consumer<Path> consumer) throws IOException {
        if (resourceSiteDir != null) {
            ClassPathUtils.consumeAsPaths(PathUtils.join(resourceSiteDir, resource), consumer);
        }
    }

    public void consumeSite(Consumer<Path> consumer) throws IOException {
        if (resourceSiteDir != null) {
            ClassPathUtils.consumeAsPaths(resourceSiteDir, consumer);
        }
    }

}
