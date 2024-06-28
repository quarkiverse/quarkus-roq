package io.quarkiverse.roq.generator.runtime;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;

import java.util.regex.Pattern;

public record SelectedPath(String path, String outputPath, PageSource source) {

    private static final Pattern NON_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9\\\\/.]");

    private SelectedPath(SelectedPathBuilder builder) {
        this(builder.path, builder.outputPath, builder.source);
    }

    public static SelectedPathBuilder builder() {
        return new SelectedPathBuilder();
    }

    public static String defaultOutputPath(String path) {
        String staticPath = path;
        if (staticPath.endsWith("/")) {
            staticPath = cleanPath(staticPath) + "index.html";
        } else if (NON_FILE_CHARS.matcher(path).find()) {
            staticPath = cleanPath(staticPath);
        }
        return staticPath;
    }

    private static String cleanPath(String staticPath) {
        return NON_FILE_CHARS.matcher(staticPath).replaceAll("-");
    }

    public static class SelectedPathBuilder {
        String path;
        PageSource source = PageSource.PROVIDED;
        String outputPath = null;

        public SelectedPathBuilder path(String path) {
            this.path = path;
            return this;
        }

        SelectedPathBuilder sourceConfig() {
            this.source = PageSource.CONFIG;
            return this;
        }

        public SelectedPathBuilder html(String path) {
            this.path = path;
            this.outputPath = defaultOutputPath(addTrailingSlash(path));
            return this;
        }

        public SelectedPathBuilder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public SelectedPath build() {
            if (outputPath == null) {
                outputPath = defaultOutputPath(path);
            }
            if (outputPath.startsWith("/")) {
                outputPath = outputPath.substring(1);
            }
            return new SelectedPath(this);
        }
    }
}
