package io.quarkiverse.roq.deployment.items;

import java.nio.file.Path;

import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.util.ProjectUtils;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.builder.item.SimpleBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;

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
