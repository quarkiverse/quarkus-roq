package io.quarkiverse.roq.runtime;

import static io.quarkiverse.roq.runtime.util.PathUtils.addTrailingSlash;

import java.util.regex.Pattern;

public final record StaticPage(String path, String outputPath, PageType type) {

    private static final Pattern NON_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9\\\\/.]");

    private StaticPage(StaticPageBuilder builder) {
        this(builder.path, builder.outputPath, builder.type);
    }

    public static StaticPageBuilder builder() {
        return new StaticPageBuilder();
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

    public String path() {
        return path;
    }

    public PageType type() {
        return type;
    }

    public String outputPath() {
        return outputPath;
    }

    public static class StaticPageBuilder {
        String path;
        PageType type = PageType.PROVIDED;
        String outputPath = null;

        public StaticPageBuilder path(String path) {
            this.path = path;
            return this;
        }

        StaticPageBuilder fixed() {
            this.type = PageType.FIXED;
            return this;
        }

        public StaticPageBuilder html(String path) {
            this.path = path;
            this.outputPath = defaultOutputPath(addTrailingSlash(path));
            return this;
        }

        public StaticPageBuilder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public StaticPage build() {
            if (outputPath == null) {
                outputPath = defaultOutputPath(path);
            }
            if (outputPath.startsWith("/")) {
                outputPath = outputPath.substring(1);
            }
            return new StaticPage(this);
        }
    }
}
