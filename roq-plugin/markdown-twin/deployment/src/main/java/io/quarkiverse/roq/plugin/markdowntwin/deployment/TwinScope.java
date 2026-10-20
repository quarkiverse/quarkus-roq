package io.quarkiverse.roq.plugin.markdowntwin.deployment;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Decides which pages get a twin, from the configured include and exclude patterns.
 * <p>
 * Patterns are matched against the page path relative to the site root, without a leading or trailing slash: a
 * directory-style page such as {@code guides/all-config/} is matched as {@code guides/all-config}, for a glob and for a
 * regex alike, and a pattern written with a leading or trailing slash, the way a URL appears, is read the same way, so
 * that the patterns can be reused verbatim in a CDN rule. A directory-style page is also matched as the index of its
 * directory, {@code guides/index} for {@code guides/}, so that a tree pattern such as {@code /guides/**} covers the
 * landing page of the tree.
 */
final class TwinScope {

    private static final String GLOB_PREFIX = "glob:";
    private static final String REGEX_PREFIX = "regex:";

    private final List<Predicate<String>> include;
    private final List<Predicate<String>> exclude;

    private TwinScope(List<Predicate<String>> include, List<Predicate<String>> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    static final String INCLUDE_PROPERTY = "quarkus.roq.plugin.markdown-twin.include";
    static final String EXCLUDE_PROPERTY = "quarkus.roq.plugin.markdown-twin.exclude";

    static TwinScope of(MarkdownTwinConfig config) {
        return new TwinScope(compile(config.include(), INCLUDE_PROPERTY), compile(config.exclude(), EXCLUDE_PROPERTY));
    }

    /**
     * Whether the page at this resource path is in scope: it matches an include pattern, or there are none, and it
     * matches no exclude pattern.
     */
    boolean includes(String resourcePath) {
        if (resourcePath == null) {
            return false;
        }
        final String path = normalizePath(resourcePath);
        // The landing page of a directory is also its index, so a tree pattern matches it.
        final String index = resourcePath.endsWith("/") ? path + "/index" : null;
        if (!include.isEmpty() && !anyMatch(include, path, index)) {
            return false;
        }
        return !anyMatch(exclude, path, index);
    }

    private boolean anyMatch(List<Predicate<String>> matchers, String path, String index) {
        for (Predicate<String> matcher : matchers) {
            if (matcher.test(path) || (index != null && matcher.test(index))) {
                return true;
            }
        }
        return false;
    }

    private static List<Predicate<String>> compile(Optional<List<String>> patterns, String property) {
        final List<Predicate<String>> matchers = new ArrayList<>();
        for (String pattern : patterns.orElse(List.of())) {
            if (pattern == null || pattern.isBlank()) {
                continue;
            }
            try {
                matchers.add(matcher(normalizePath(pattern.strip())));
            } catch (IllegalArgumentException e) {
                // A glob with braces is split on its commas unless they are escaped as \, in the property value.
                throw new IllegalArgumentException(property + ": invalid pattern '" + pattern + "': " + e.getMessage()
                        + " (a comma inside a pattern must be written as \\, in the property value)", e);
            }
        }
        return List.copyOf(matchers);
    }

    /**
     * A bare pattern is a glob, matching {@code site.ignored-files}. A leading or trailing slash is dropped on both
     * sides so that {@code /guides/**} and {@code guides/**} behave the same, and {@code /guides/all-config/} names
     * the page it shows in the browser.
     * <p>
     * A glob is compiled by the default file system, the way Roq compiles {@code site.ignored-files}. A regex is
     * matched against the path string directly instead: a {@link PathMatcher} matches a regex against
     * {@link Path#toString()}, which is backslash-separated on Windows, so a regex written with slashes would match
     * nothing there.
     */
    private static Predicate<String> matcher(String pattern) {
        if (pattern.startsWith(REGEX_PREFIX)) {
            return Pattern.compile(stripLeadingSlash(pattern.substring(REGEX_PREFIX.length()))).asMatchPredicate();
        }
        final String glob = pattern.startsWith(GLOB_PREFIX) ? pattern.substring(GLOB_PREFIX.length()) : pattern;
        final PathMatcher matcher = Path.of("").getFileSystem().getPathMatcher(GLOB_PREFIX + stripLeadingSlash(glob));
        return path -> {
            try {
                return matcher.matches(Path.of(path));
            } catch (InvalidPathException e) {
                // A URL path may hold characters a Windows file name cannot; such a page is simply not matched.
                return false;
            }
        };
    }

    private static String normalizePath(String resourcePath) {
        final String path = stripLeadingSlash(resourcePath);
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static String stripLeadingSlash(String value) {
        return value.startsWith("/") ? value.substring(1) : value;
    }
}
