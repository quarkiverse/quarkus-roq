package io.quarkiverse.roq.migration;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * File operations that site migrators need when moving a site into the Roq layout.
 */
public final class SiteFiles {

    private SiteFiles() {
    }

    /**
     * Move every file under {@code source} to the same relative path under {@code target}, merging with what is
     * already there, then delete {@code source}.
     */
    public static void moveTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        for (Path file : files(source, p -> true)) {
            Path destination = target.resolve(source.relativize(file).toString());
            Files.createDirectories(destination.getParent());
            Files.move(file, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        deleteTree(source);
    }

    /**
     * Copy every file under {@code source} to the same relative path under {@code target}.
     */
    public static void copyTree(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) {
            return;
        }
        for (Path file : files(source, p -> true)) {
            Path destination = target.resolve(source.relativize(file).toString());
            Files.createDirectories(destination.getParent());
            Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static void deleteTree(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * @return the regular files under {@code dir} that match, sorted; empty if {@code dir} is not a directory
     */
    public static List<Path> files(Path dir, Predicate<Path> filter) throws IOException {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            return paths.filter(Files::isRegularFile).filter(filter).sorted().toList();
        }
    }
}
