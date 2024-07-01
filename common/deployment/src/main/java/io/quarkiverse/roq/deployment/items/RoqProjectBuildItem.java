package io.quarkiverse.roq.deployment.items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.util.ClassPathUtils;

public final class RoqProjectBuildItem extends SimpleBuildItem {
    private final RoqProject project;
    private final String resourceSiteDir;

    public RoqProjectBuildItem(RoqProject project, String resourceSiteDir) {
        this.project = project;
        this.resourceSiteDir = resourceSiteDir;
    }

    public RoqProject project() {
        return project;
    }

    public boolean isActive() {
        return project != null || resourceSiteDir != null;
    }

    public void consumePathFromSite(String resource, Consumer<Path> consumer) throws IOException {
        if (resourceSiteDir != null) {
            ClassPathUtils.consumeAsPaths(PathUtils.join(resourceSiteDir, resource), consumer);
        }
        if (project != null) {
            consumer.accept(project.siteDir().resolve(resource));
        }
    }

    /**
     * Container to store resolved directory locations.
     */
    public record RoqProject(
            /**
             * The root directory of the project
             */
            Path rootDir,
            /**
             * The site directory of the project defaults to /src/main/site
             */
            Path siteDir) {

    }
}
