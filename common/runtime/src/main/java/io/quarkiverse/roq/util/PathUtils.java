package io.quarkiverse.roq.util;

import java.util.Arrays;
import java.util.stream.Collectors;

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

    public static String join(String path1, String... other) {
        if (other == null || other.length == 0) {
            return path1;
        }
        if (other.length == 1) {
            return addTrailingSlash(path1) + removeLeadingSlash(other[0]);
        }
        final String otherJoined = Arrays.stream(other).map(PathUtils::removeTrailingSlash).collect(Collectors.joining("/"));
        return addTrailingSlash(path1) + removeLeadingSlash(otherJoined);
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

    public static String fileName(String path) {
        final int i = path.lastIndexOf("/");
        if (i == -1) {
            return path;
        }
        return path.substring(i + 1);
    }
}
