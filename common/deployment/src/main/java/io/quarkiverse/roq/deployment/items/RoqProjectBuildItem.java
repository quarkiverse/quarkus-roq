package io.quarkiverse.roq.deployment.items;

import java.nio.file.Path;

import io.quarkus.builder.item.SimpleBuildItem;

public final class RoqProjectBuildItem extends SimpleBuildItem {
    private final RoqProject project;

    public RoqProjectBuildItem(RoqProject project) {
        this.project = project;
    }

    public RoqProject dirs() {
        return project;
    }

    // TODO: in the future we might want to include a Scanner like in web-bundler to see if dependencies contains roq stuff

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
