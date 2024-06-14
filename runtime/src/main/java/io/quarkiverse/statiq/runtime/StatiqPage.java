package io.quarkiverse.statiq.runtime;

import static io.quarkiverse.statiq.runtime.util.PathUtils.addTrailingSlash;

import java.util.regex.Pattern;

public final record StatiqPage(String path, String outputPath, PageType type) {

    private static final Pattern NON_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9\\\\/.]");

    private StatiqPage(StatiqPageBuilder builder) {
        this(builder.path, builder.outputPath, builder.type);
    }

    public static StatiqPageBuilder builder() {
        return new StatiqPageBuilder();
    }

    public static String defaultOutputPath(String path) {
        String statiqPath = path;
        if (statiqPath.endsWith("/")) {
            statiqPath = cleanPath(statiqPath) + "index.html";
        } else if (NON_FILE_CHARS.matcher(path).find()) {
            statiqPath = cleanPath(statiqPath);
        }
        return statiqPath;
    }

    private static String cleanPath(String statiqPath) {
        return NON_FILE_CHARS.matcher(statiqPath).replaceAll("-");
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

    public static class StatiqPageBuilder {
        String path;
        PageType type = PageType.PROVIDED;
        String outputPath = null;

        public StatiqPageBuilder path(String path) {
            this.path = path;
            return this;
        }

        StatiqPageBuilder fixed() {
            this.type = PageType.FIXED;
            return this;
        }

        public StatiqPageBuilder html(String path) {
            this.path = path;
            this.outputPath = defaultOutputPath(addTrailingSlash(path));
            return this;
        }

        public StatiqPageBuilder outputPath(String outputPath) {
            this.outputPath = outputPath;
            return this;
        }

        public StatiqPage build() {
            if (outputPath == null) {
                outputPath = defaultOutputPath(path);
            }
            if (outputPath.startsWith("/")) {
                outputPath = outputPath.substring(1);
            }
            return new StatiqPage(this);
        }
    }
}
