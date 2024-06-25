package io.quarkiverse.roq.deployment.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Container to store resolved directory locations.
 */
public class RoqProjectDirectories {

    /**
     * The root directory of the project
     */
    private final Path rootDir;

    /**
     * The site directory of the project defaults to /src/main/site
     */
    private final Path siteDir;

    /**
     * The data directory defaults to "data/" relative to root directory.
     */
    private final Path dataDir;

    public RoqProjectDirectories(Path rootDir, Path siteDir, Path dataDir) {
        this.rootDir = rootDir;
        this.siteDir = siteDir;
        this.dataDir = dataDir;
    }

    public Path getRootDir() {
        return rootDir;
    }

    public Path getSiteDir() {
        return siteDir;
    }

    public Path getDataDir() {
        return dataDir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RoqProjectDirectories that = (RoqProjectDirectories) o;
        return Objects.equals(getRootDir(), that.getRootDir()) && Objects.equals(getSiteDir(), that.getSiteDir())
                && Objects.equals(getDataDir(), that.getDataDir());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRootDir(), getSiteDir(), getDataDir());
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RoqProjectDirectories.class.getSimpleName() + "[", "]")
                .add("rootDir=" + rootDir)
                .add("siteDir=" + siteDir)
                .add("dataDir=" + dataDir)
                .toString();
    }

    public static Path findProjectRoot(Path outputDirectory) {
        Path currentPath = outputDirectory;
        do {
            if (Files.exists(currentPath.resolve(Paths.get("src", "main")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.properties")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yaml")))
                    || Files.exists(currentPath.resolve(Paths.get("config", "application.yml")))) {
                return currentPath.normalize();
            }
            if (currentPath.getParent() != null && Files.exists(currentPath.getParent())) {
                currentPath = currentPath.getParent();
            } else {
                return null;
            }
        } while (true);
    }
}