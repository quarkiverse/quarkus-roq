package io.quarkiverse.roq.deployment.items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.paths.PathVisit;

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

    public void consumePathFromRoqResourceDir(String resource, Consumer<PathVisit> consumer) throws IOException {
        if (roqResourceDir != null) {
            visitRuntimeResources(PathUtils.join(roqResourceDir, resource), consumer);
        }
    }

    public void consumePathFromRoqDir(String resource, Consumer<Path> consumer) throws IOException {
        if (project != null) {
            consumer.accept(project.roqDir().resolve(resource));
        }
    }

    public void consumeRoqResourceDir(Consumer<PathVisit> consumer) throws IOException {
        if (roqResourceDir != null) {
            visitRuntimeResources(roqResourceDir, consumer);
        }
    }

    public void consumeRoqDir(Consumer<Path> consumer) throws IOException {
        if (project != null) {
            consumer.accept(project.roqDir());
        }
    }

    public boolean isRoqResourcesInRoot() {
        return roqResourceDir != null && roqResourceDir.isEmpty();
    }

    public String roqResourceDir() {
        return roqResourceDir;
    }

    public static void visitRuntimeResources(String resourceName, Consumer<PathVisit> visitor) {
        final Set<String> visited = new HashSet<>();
        // There is an issue in visitRuntimeResources calling visitor multiple time with the same resource.
        QuarkusClassLoader.visitRuntimeResources(resourceName, p -> {
            if (visited.add(p.getUrl().toExternalForm())) {
                visitor.accept(p);
            }
        });
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
