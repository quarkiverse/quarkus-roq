package io.quarkus.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
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

    private static final Pattern ATTR_PATTERN = Pattern.compile("(?i)\\b(src|href|srcset)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')");

    public record Result(int filesRenamed, int htmlFilesUpdated) {
    }

    public Result rename(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return new Result(0, 0);
        }

        // Pass 1: find and collect files with parentheses and HTML files
        // HTML files are tracked by their eventual (post-rename) path so they remain readable
        // even when the HTML file itself has parentheses in its name.
        List<Path> htmlFiles = new ArrayList<>();
        Map<Path, Path> renames = new HashMap<>(); // source -> target (absolute paths)
        Map<String, String> renamedFileNames = new HashMap<>(); // oldName -> newName (for HTML reference updates)

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = file.getFileName().toString();
                boolean hasParens = name.contains("(") || name.contains(")");
                if (hasParens) {
                    String newName = name.replace('(', '-').replace(")", "");
                    Path target = file.resolveSibling(newName);
                    renames.put(file, target);
                    renamedFileNames.put(name, newName);
                    // If the HTML file itself is being renamed, track its future path
                    if (name.endsWith(".html")) {
                        htmlFiles.add(target);
                    }
                } else if (name.endsWith(".html")) {
                    htmlFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        // Pre-check for collision risks before performing any moves:
        // 1. Two different sources rename to the same target
        // 2. A target already exists as a non-renamed file
        Set<Path> targets = new HashSet<>();
        for (Map.Entry<Path, Path> entry : renames.entrySet()) {
            Path target = entry.getValue();
            if (!targets.add(target)) {
                throw new FileAlreadyExistsException(
                        "Cannot rename '" + entry.getKey() + "' to '" + target
                                + "' because another source file renames to the same target");
            }
            if (Files.exists(target) && !renames.containsKey(target)) {
                throw new FileAlreadyExistsException(
                        "Cannot rename '" + entry.getKey() + "' to '" + target
                                + "' because target file already exists");
            }
        }

        // Perform renames; on failure, roll back already-completed moves
        List<Path[]> completed = new ArrayList<>();
        try {
            for (Map.Entry<Path, Path> entry : renames.entrySet()) {
                Files.move(entry.getKey(), entry.getValue());
                completed.add(new Path[] { entry.getKey(), entry.getValue() });
            }
        } catch (IOException e) {
            // Best-effort rollback of completed renames
            for (int i = completed.size() - 1; i >= 0; i--) {
                try {
                    Files.move(completed.get(i)[1], completed.get(i)[0]);
                } catch (IOException ignored) {
                    // Rollback is best-effort; original exception is more important
                }
            }
            throw e;
        }

        // Pass 2: update references in HTML files scoped to src, href, and srcset attributes
        int updatedCount = 0;
        if (!renamedFileNames.isEmpty()) {
            for (Path html : htmlFiles) {
                String content = Files.readString(html, StandardCharsets.UTF_8);
                String updated = updateAttributeReferences(content, renamedFileNames);
                if (!updated.equals(content)) {
                    Files.writeString(html, updated, StandardCharsets.UTF_8);
                    updatedCount++;
                }
            }
        }

        return new Result(renames.size(), updatedCount);
    }

    private String updateAttributeReferences(String content, Map<String, String> renamedFileNames) {
        Matcher matcher = ATTR_PATTERN.matcher(content);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String attrName = matcher.group(1);
            String quote = matcher.group(3) != null ? "\"" : "'";
            String attrValue = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);

            String updatedValue = attrValue;
            for (Map.Entry<String, String> entry : renamedFileNames.entrySet()) {
                updatedValue = updatedValue.replace(entry.getKey(), entry.getValue());
            }

            String replacement = attrName + "=" + quote + updatedValue + quote;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
