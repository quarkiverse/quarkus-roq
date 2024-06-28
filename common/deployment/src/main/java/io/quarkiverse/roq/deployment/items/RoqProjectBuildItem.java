package io.quarkiverse.roq.deployment.items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqProjectBuildItem extends SimpleBuildItem {
    private final RoqProject project;

    public RoqProjectBuildItem(RoqProject project) {
        this.project = project;
    }

    public RoqProject dirs() {
        return project;
    }

    public void consumePathFromSite(String resource, Consumer<Path> consumer) throws IOException {
        // TODO: in the future we might want to scan dependencies when configured
        consumer.accept(project.siteDir().resolve(resource));
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
