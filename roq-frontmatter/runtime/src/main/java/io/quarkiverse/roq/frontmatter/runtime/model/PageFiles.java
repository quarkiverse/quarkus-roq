package io.quarkiverse.roq.frontmatter.runtime.model;

import static io.quarkiverse.tools.stringpaths.StringPaths.fileExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.slugify;

import java.util.List;

public record PageFiles(List<String> names, boolean slugified) {

    public static final PageFiles EMPTY = new PageFiles(List.of(), false);

    public static PageFiles empty() {
        return EMPTY;
    }

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
        final String extension = fileExtension(filePath);
        String path = removeExtension(filePath);
        // We allow dots because some static files might have versions in their names
        // Anyway they have an extension
        String slugify = slugify(path, true, true);
        return extension == null ? slugify : slugify + "." + extension;
    }
}
