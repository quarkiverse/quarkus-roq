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
public class ConfiguredPathsProvider {

    private static volatile String targetDir;
    @Inject
    RoqGeneratorConfig config;

    private static volatile Set<String> staticPaths;
    private static volatile List<String> selectedPaths;

    public static void setStaticPaths(Set<String> staticPaths) {
        ConfiguredPathsProvider.staticPaths = staticPaths;
    }

    public static void setOutputTarget(String targetDir) {
        ConfiguredPathsProvider.targetDir = targetDir;
    }

    public static void setSelectedPaths(List<String> selectedPaths) {
        ConfiguredPathsProvider.selectedPaths = selectedPaths;
    }

    public static String targetDir() {
        return targetDir;
    }

    @Produces
    @Singleton
    RoqSelection produce() {
        List<SelectedPath> selectedPaths = new ArrayList<>();
        for (String p : config.paths().orElse(List.of())) {
            if (!isGlobPattern(p) && p.startsWith("/")) {
                // fixed paths are directly added
                selectedPaths.add(SelectedPath.builder().path(p).sourceConfig().build());
                continue;
            }
            if (ConfiguredPathsProvider.staticPaths != null) {
                // Try to detect fixed paths from glob pattern
                for (String staticPath : ConfiguredPathsProvider.staticPaths) {
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                    if (matcher.matches(Path.of(staticPath))) {
                        selectedPaths.add(SelectedPath.builder().sourceConfig().path(staticPath).build());
                    }
                }
            }

        }
        for (String selectedPath : ConfiguredPathsProvider.selectedPaths) {
            selectedPaths.add(SelectedPath.builder().sourceBuildItem().path(selectedPath).build());
        }
        return new RoqSelection(selectedPaths);
    }

    private static boolean isGlobPattern(String s) {
        // Check if the string contains any glob pattern special characters
        return s.contains("*") || s.contains("?") || s.contains("{") || s.contains("}") || s.contains("[") || s.contains("]")
                || s.contains("**");
    }
}
