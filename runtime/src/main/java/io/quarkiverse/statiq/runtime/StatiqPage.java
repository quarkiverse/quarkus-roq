package io.quarkiverse.statiq.runtime;

import java.util.regex.Pattern;

public record StatiqPage(String path, PageType type, String outputPath) {

    private static final Pattern NON_FILE_CHARS = Pattern.compile("[^a-zA-Z0-9\\\\/.]");

    public StatiqPage(String path, PageType type) {
        this(path, type, defaultOutputPath(path));
    }

    public StatiqPage(String path) {
        this(path, PageType.PROVIDED);
    }

    public static String defaultOutputPath(String path) {
        String statiqPath = path;
        if (statiqPath.endsWith("/")) {
            statiqPath = cleanPath(statiqPath) + "index.html";
        } else if (NON_FILE_CHARS.matcher(path).find()) {
            statiqPath = cleanPath(statiqPath);
        }
        if (statiqPath.startsWith("/")) {
            statiqPath = statiqPath.substring(1);
        }
        return statiqPath;
    }

    private static String cleanPath(String statiqPath) {
        return NON_FILE_CHARS.matcher(statiqPath).replaceAll("-");
    }

}
