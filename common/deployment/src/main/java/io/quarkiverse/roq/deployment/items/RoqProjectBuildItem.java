package io.quarkiverse.roq.deployment.items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.runtime.util.ClassPathUtils;

public final class RoqProjectBuildItem extends SimpleBuildItem {
    private final RoqProject project;
    private final String roqResourceDir;

    public RoqProjectBuildItem(RoqProject project, String roqResourceDir) {
        this.project = project;
        this.roqResourceDir = roqResourceDir;
    }

    public RoqProject project() {
        return project;
    }

    public boolean isActive() {
        return project != null || roqResourceDir != null;
    }

    public void consumePathFromRoqDir(String resource, Consumer<Path> consumer) throws IOException {
        if (roqResourceDir != null) {
            ClassPathUtils.consumeAsPaths(PathUtils.join(roqResourceDir, resource), consumer);
        }
        if (project != null) {
            consumer.accept(project.roqDir().resolve(resource));
        }
    }

    public void consumeRoqDir(Consumer<Path> consumer) throws IOException {
        if (roqResourceDir != null) {
            ClassPathUtils.consumeAsPaths(roqResourceDir, consumer);
        }
        if (project != null) {
            consumer.accept(project.roqDir());
        }
    }

    /**
     * Container to store resolved directory locations.
     */
    public record RoqProject(
            /*
             * The root directory of the project
             */
            Path rootDir,
            /*
             * The roq directory of the project defaults is the rootDir
             */
            Path roqDir) {

    }
}