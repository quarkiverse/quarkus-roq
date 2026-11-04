package io.quarkiverse.roq.migration.engine;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Read-only view of the site being migrated, given to the preflight and analyze stages of a migration.
 * <p>
 * It deliberately exposes no {@link Path}: all access goes through relative paths (using {@code /}), so those stages
 * cannot write to the original site.
 */
public final class SourceSite {

    /**
     * Directories that are never part of the site: version control, build output and dependency caches.
     * They are not listed by {@link #files(String)} and not copied to the output directory.
     */
    public static final Set<String> IGNORED_DIRS = Set.of(".git", "_site", ".jekyll-cache", ".sass-cache", ".bundle",
            "node_modules", "vendor", "target");

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dir;

    SourceSite(Path dir) {
        this.dir = dir;
    }

    /**
     * @return the name of the site directory, e.g. {@code my-blog}
     */
    public String name() {
        return dir.getFileName().toString();
    }

    public boolean exists(String path) {
        return Files.exists(resolve(path));
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

    public String readString(String path) throws MigrationException {
        try {
            return Files.readString(resolve(path));
        } catch (IOException e) {
            throw new MigrationException("Cannot read " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * @return the names of the entries directly inside a directory ({@code ""} for the site root), sorted;
     *         empty if it is not a directory
     */
    public List<String> children(String path) throws MigrationException {
        Path target = resolve(path);
        if (!Files.isDirectory(target)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(target)) {
            return entries.map(p -> p.getFileName().toString()).sorted().toList();
        } catch (IOException e) {
            throw new MigrationException("Cannot list " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * @return paths, relative to the site root, of every regular file under a directory ({@code ""} for the whole
     *         site), skipping {@link #IGNORED_DIRS}; empty if it is not a directory
     */
    public List<String> files(String path) throws MigrationException {
        Path start = resolve(path);
        if (!Files.isDirectory(start)) {
            return List.of();
        }
        List<String> files = new ArrayList<>();
        try {
            Files.walkFileTree(start, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (!d.equals(start) && IGNORED_DIRS.contains(d.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        files.add(relative(file));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new MigrationException("Cannot list files in " + path + ": " + e.getMessage(), e);
        }
        files.sort(null);
        return files;
    }

    private Path resolve(String path) {
        Path resolved = dir.resolve(path).normalize();
        if (!resolved.startsWith(dir)) {
            throw new IllegalArgumentException("Path is outside the site: " + path);
        }
        return resolved;
    }

    private String relative(Path file) {
        return dir.relativize(file).toString().replace('\\', '/');
    }
}
