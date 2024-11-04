package io.quarkiverse.roq.util;

import java.util.Objects;

public final class PathUtils {

    public static String toUnixPath(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.replaceAll("\\\\", "/");
    }

    public static String prefixWithSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.startsWith("/") ? path : "/" + path;
    }

    public static String surroundWithSlashes(String path) {
        return prefixWithSlash(addTrailingSlash(path));
    }

    public static String addTrailingSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.endsWith("/") ? path : path + "/";
    }

    public static String join(String path1, String path2) {
        Objects.requireNonNull(path1, "path1 is required");
        if (path2 == null) {
            return path1;
        }
        if (path1.isEmpty()) {
            return path2;
        }
        return addTrailingSlash(path1) + removeLeadingSlash(path2);
    }

    public static String removeLeadingSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.startsWith("/") ? path.substring(1) : path;
    }

    public static String removeTrailingSlash(String path) {
        Objects.requireNonNull(path, "path is required");
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    public static String removeExtension(String path) {
        Objects.requireNonNull(path, "path is required");
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(0, i) : path;
    }

    public static String getExtension(String path) {
        Objects.requireNonNull(path, "path is required");
        final int i = path.lastIndexOf(".");
        return i > 0 ? path.substring(i + 1) : null;
    }

    public static String fileName(String path) {
        Objects.requireNonNull(path, "path is required");
        final int i = path.lastIndexOf("/");
        if (i == -1) {
            return path;
        }
        return path.substring(i + 1);
    }
}
