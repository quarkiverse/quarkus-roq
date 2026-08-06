package io.quarkus.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renames files with parentheses under a directory and updates HTML references.
 * <p>
 * Roq's static file handler silently renames files with parentheses
 * (e.g. {@code content(1)} becomes {@code content-1}) during the build, but does not
 * update HTML references, so images 404 at runtime.
 * <p>
 * This class fixes the source files before the build: it renames files to replace
 * parentheses with dashes and updates all {@code .html} files in the same tree.
 */
public class ParenthesisFileRenamer {

    private static final Pattern PAREN_REFERENCE = Pattern.compile("([a-zA-Z0-9_-]+)\\((\\d+)\\)");

    public record Result(int filesRenamed, int htmlFilesUpdated) {
    }

    public Result rename(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return new Result(0, 0);
        }

        // Pass 1: find and rename files with parentheses
        List<Path> htmlFiles = new ArrayList<>();
        Map<Path, String> renames = new HashMap<>();

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                if (name.endsWith(".html")) {
                    htmlFiles.add(file);
                }
                if (name.contains("(")) {
                    String newName = name.replace('(', '-').replace(")", "");
                    renames.put(file, newName);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (Map.Entry<Path, String> entry : renames.entrySet()) {
            Path source = entry.getKey();
            Path target = source.resolveSibling(entry.getValue());
            Files.move(source, target);
        }

        // Pass 2: update references in HTML files
        int updatedCount = 0;
        for (Path html : htmlFiles) {
            String content = Files.readString(html, StandardCharsets.UTF_8);
            String updated = PAREN_REFERENCE.matcher(content).replaceAll("$1-$2");
            if (!updated.equals(content)) {
                Files.writeString(html, updated, StandardCharsets.UTF_8);
                updatedCount++;
            }
        }

        return new Result(renames.size(), updatedCount);
    }
}
