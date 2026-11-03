package io.quarkiverse.roq.migration.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only view of the site being migrated, given to the preflight and analyze stages of a migration.
 * <p>
 * It deliberately exposes no {@link Path}: all access goes through relative paths (using {@code /}), so those stages
 * cannot write to the original site.
 */
public final class SourceSite {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dir;

    SourceSite(Path dir) {
        this.dir = dir;
    }

    public boolean isFile(String path) {
        return Files.isRegularFile(resolve(path));
    }

    public boolean isDirectory(String path) {
        return Files.isDirectory(resolve(path));
    }

    public boolean anyFile(String... paths) {
        return Arrays.stream(paths).anyMatch(this::isFile);
    }

    /**
     * @return true if the file exists and contains the text; false if it cannot be read
     */
    public boolean contains(String path, String text) {
        if (!isFile(path)) {
            return false;
        }
        try {
            return Files.readString(resolve(path)).contains(text);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * @return true if {@code package.json} lists the package in its dependencies or devDependencies;
     *         false if there is no readable {@code package.json}
     */
    public boolean npmDependency(String name) {
        if (!isFile("package.json")) {
            return false;
        }
        try {
            JsonNode root = JSON.readTree(resolve("package.json").toFile());
            return root.path("dependencies").has(name) || root.path("devDependencies").has(name);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private Path resolve(String path) {
        Path resolved = dir.resolve(path).normalize();
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("Path is outside the site: " + path);
        }
        return resolved;
    }
}
