package io.quarkiverse.roq.generator.runtime;

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
    private static volatile String targetDir;
    @Inject
    RoqGeneratorConfig config;

    private static volatile Set<String> staticPaths;

    public static void setStaticPaths(Set<String> staticPaths) {
        FixedStaticPagesProvider.staticPaths = staticPaths;
    }

    public static void setOutputTarget(String targetDir) {
        FixedStaticPagesProvider.targetDir = targetDir;
    }

    public static String targetDir() {
        return targetDir;
    }

    @Produces
    @Singleton
    StaticPages produce() {
        List<StaticPage> StaticPages = new ArrayList<>();
        for (String p : config.fixed().orElse(List.of())) {

            if (!isGlobPattern(p) && p.startsWith("/")) {
                // fixed paths are directly added
                StaticPages.add(StaticPage.builder().path(p).fixed().build());
                continue;
            }
            if (staticPaths != null) {
                // Try to detect fixed paths from glob pattern
                for (String staticPath : staticPaths) {
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                    if (matcher.matches(Path.of(staticPath))) {
                        StaticPages.add(StaticPage.builder().fixed().path(staticPath).build());
                    }
                }
            }

        }
        return new StaticPages(StaticPages);
    }

    private static boolean isGlobPattern(String s) {
        // Check if the string contains any glob pattern special characters
        return s.contains("*") || s.contains("?") || s.contains("{") || s.contains("}") || s.contains("[") || s.contains("]")
                || s.contains("**");
    }
}
