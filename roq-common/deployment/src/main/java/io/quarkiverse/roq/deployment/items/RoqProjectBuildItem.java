package io.quarkiverse.roq.deployment.items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.util.ProjectUtils;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public final class RoqProjectBuildItem extends SimpleBuildItem {
    private final RoqLocalDir local;
    private final String roqResourceDir;

    public RoqProjectBuildItem(RoqLocalDir local, String roqResourceDir) {
        this.local = local;
        this.roqResourceDir = roqResourceDir;
    }

    public RoqLocalDir local() {
        return local;
    }

    public void addScannerForLocalRoqDir(BuildProducer<ScanLocalDirBuildItem> producer, String subDir) {
        final Path resolved = fromLocalRoqDir(subDir);
        if (resolved != null) {
            producer.produce(new ScanLocalDirBuildItem(
                    // dir to scan is from root
                    resolved,
                    // index base is from roq dir
                    local().projectRoot().relativize(local().roqDir()).toString()));
        }
    }

    public Path fromLocalRoqDir(String subDir) {
        if (local == null) {
            return null;
        }
        return ProjectUtils.resolveSubDir(local().roqDir(), subDir);
    }

    public boolean isActive() {
        return local != null || roqResourceDir != null;
    }

    public boolean isRoqResourcesInRoot() {
        return roqResourceDir != null && roqResourceDir.isEmpty();
    }

    public String roqResourceDir() {
        return roqResourceDir;
    }

    public String resolveRoqResourceSubDir(String subDir) {
        if (isRoqResourcesInRoot()) {
            return subDir;
        }
        return StringPaths.join(roqResourceDir, subDir);
    }

    /**
     * Walk a local directory recursively and register every subdirectory as a
     * {@link HotDeploymentWatchedFileBuildItem}. This lets Quarkus detect directory
     * mtime changes (from new/deleted files) during doScan(forceRestart=false).
     */
    public static void watchDirRecursively(Path dir, BuildProducer<HotDeploymentWatchedFileBuildItem> watch) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isDirectory).forEach(d -> watch.produce(HotDeploymentWatchedFileBuildItem.builder()
                    .setLocation(d.toAbsolutePath().toString()).build()));
        } catch (IOException e) {
            // directory not accessible, skip
        }
    }

    /**
     * Container to store resolved directory locations.
     */
    public record RoqLocalDir(
            /*
             * The root directory of the project
             */
            Path projectRoot,

            /*
             * The roq directory of the project defaults is the rootDir
             */
            Path roqDir) {

    }
}
