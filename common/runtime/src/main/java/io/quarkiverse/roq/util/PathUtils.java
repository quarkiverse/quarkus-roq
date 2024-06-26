package io.quarkiverse.roq.util;

public final class PathUtils {

    public static String toUnixPath(String path) {
        return path.replaceAll("\\\\", "/");
    }

    public static String prefixWithSlash(String path) {
        return path.startsWith("/") ? path : "/" + path;
    }

    public static String surroundWithSlashes(String path) {
        return prefixWithSlash(addTrailingSlash(path));
    }

    public static String addTrailingSlash(String path) {
        return path.endsWith("/") ? path : path + "/";
    }

    public static String join(String path1, String path2) {
        return addTrailingSlash(path1) + removeLeadingSlash(path2);
    }

    public static String removeLeadingSlash(String path) {
        return path.startsWith("/") ? path.substring(1) : path;
    }

    public static String removeTrailingSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    public static String removeExtension(String path) {
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(0, i) : path;
    }
}
