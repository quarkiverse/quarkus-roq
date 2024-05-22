package io.quarkiverse.statiq.runtime;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class FixedStaticPagesProvider {
    @Inject
    StatiqGeneratorConfig config;

    private static volatile Set<String> staticPaths;

    public static void setStaticPaths(Set<String> staticPaths) {
        FixedStaticPagesProvider.staticPaths = staticPaths;
    }

    @Produces
    @Singleton
    StatiqPages produce() {
        List<StatiqPage> statiqPages = new ArrayList<>();
        for (String p : config.fixedPaths) {

            if (!isGlobPattern(p) && p.startsWith("/")) {
                // fixed paths are directly added
                statiqPages.add(new StatiqPage(p, PageType.FIXED));
                continue;
            }
            // Try to detect fixed paths from glob pattern
            for (String staticPath : staticPaths) {
                PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                if (matcher.matches(Path.of(staticPath))) {
                    statiqPages.add(new StatiqPage(staticPath, PageType.FIXED));
                }
            }
        }
        return new StatiqPages(statiqPages);
    }

    private static boolean isGlobPattern(String s) {
        // Check if the string contains any glob pattern special characters
        return s.contains("*") || s.contains("?") || s.contains("{") || s.contains("}") || s.contains("[") || s.contains("]")
                || s.contains("**");
    }
}
