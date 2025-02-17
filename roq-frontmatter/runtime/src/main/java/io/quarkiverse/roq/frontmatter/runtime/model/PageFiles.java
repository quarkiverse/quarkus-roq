package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.roq.util.PathUtils.getExtension;
import static io.quarkiverse.roq.util.PathUtils.removeExtension;

import java.util.List;

import io.quarkiverse.roq.util.PathUtils;

public record PageFiles(List<String> names, boolean slugified) {
    public boolean contains(Object o) {
        return names.contains(o);
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    public int size() {
        return names.size();
    }

    public static String slugifyFile(String filePath) {
        final String extension = getExtension(filePath);
        String path = removeExtension(filePath);
        // We allow dots because some static files might have versions in their names
        // Anyway they have an extension
        return PathUtils.slugify(path, true, true) + "." + extension;
    }
}
