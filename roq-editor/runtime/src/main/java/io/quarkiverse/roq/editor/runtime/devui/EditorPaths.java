package io.quarkiverse.roq.editor.runtime.devui;

import java.nio.file.Path;
import java.util.Collection;

/**
 * Path resolution helpers for the editor write actions.
 */
public final class EditorPaths {

    private EditorPaths() {
    }

    /**
     * Resolves the file of a new page from a user provided path.
     *
     * @param contentDir the site content directory
     * @param path the path of the new page, relative to the content directory (e.g. {@code about/index.md} or
     *        {@code about.md})
     * @param extension the expected file extension (without the dot)
     * @return the absolute normalized file path
     * @throws IllegalArgumentException if the path is not a valid page path inside the content directory
     */
    public static Path resolveNewPagePath(Path contentDir, String path, String extension) {
        final String trimmed = path == null ? "" : path.trim().replace('\\', '/');
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Path parameter is required");
        }
        if (trimmed.startsWith("/") || trimmed.endsWith("/")) {
            throw new IllegalArgumentException("Path must be a file path relative to the content directory: " + trimmed);
        }
        for (String segment : trimmed.split("/")) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Invalid path: " + trimmed);
            }
        }
        final String suffix = "." + extension;
        if (!trimmed.endsWith(suffix) || trimmed.endsWith("/" + suffix) || trimmed.equals(suffix)) {
            throw new IllegalArgumentException("Path must end with a '%s' file name: %s".formatted(suffix, trimmed));
        }
        final Path root = contentDir.toAbsolutePath().normalize();
        final Path file = root.resolve(trimmed).normalize();
        if (!file.startsWith(root) || file.equals(root)) {
            throw new IllegalArgumentException("Path is outside the content directory: " + trimmed);
        }
        return file;
    }

    /**
     * Tells if the directory of an index page can be deleted together with the page.
     * <p>
     * It is not the case for the content directory itself, or when other pages live in that directory.
     *
     * @param contentDir the site content directory
     * @param pageFile the index page file to delete
     * @param allPageFiles the files of all the pages of the site
     * @return true if the parent directory of the page can be deleted
     */
    public static boolean canDeletePageDir(Path contentDir, Path pageFile, Collection<Path> allPageFiles) {
        final Path root = contentDir.toAbsolutePath().normalize();
        final Path file = pageFile.toAbsolutePath().normalize();
        final Path dir = file.getParent();
        if (dir == null || !dir.startsWith(root) || dir.equals(root)) {
            return false;
        }
        for (Path other : allPageFiles) {
            final Path otherFile = other.toAbsolutePath().normalize();
            if (!otherFile.equals(file) && otherFile.startsWith(dir)) {
                return false;
            }
        }
        return true;
    }
}
