package io.quarkiverse.roq.generator.runtime;

import static io.quarkiverse.roq.util.PathUtils.addTrailingSlash;
import static io.quarkiverse.roq.util.PathUtils.prefixWithSlash;

import java.util.regex.Pattern;

public record SelectedPath(String path, String outputPath, Origin source) {

    private SelectedPath(SelectedPathBuilder builder) {
        this(prefixWithSlash(builder.path), builder.outputPath, builder.source);
    }

    public SelectedPath clean(PathReplaceConfig config) {
        return new SelectedPath(path, cleanOutputPath(config, outputPath), source);
    }

    public static SelectedPathBuilder builder() {
        return new SelectedPathBuilder();
    }

    public static String defaultOutputPath(String path) {
        if (path.endsWith("/")) {
            return path + "index.html";
        }
        return path;
    }

    static String cleanOutputPath(PathReplaceConfig options, String staticPath) {
        return (options.enabled()) ? Pattern.compile(options.allowedRegex())
                .matcher(staticPath).replaceAll(options.replaceWith()) : staticPath;
    }

    public static class SelectedPathBuilder {
        String path;
        Origin source = Origin.PROVIDED;
        String outputPath = null;

        public SelectedPathBuilder path(String path) {
            this.path = path;
            return this;
        }

        public SelectedPathBuilder source(Origin source) {
            this.source = source;
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
            if (outputPath == null || outputPath.isEmpty()) {
                outputPath = defaultOutputPath(path);
            }
            if (outputPath.startsWith("/")) {
                outputPath = outputPath.substring(1);
            }
            return new SelectedPath(this);
        }
    }
}
