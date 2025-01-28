package io.quarkiverse.roq.generator.runtime;

import java.util.Map;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ConfiguredPathsProvider {

    private static volatile String targetDir;

    private static volatile RoqSelection buildSelectedPaths;
    private static volatile Map<String, StaticFile> staticFiles;

    public static void setStaticFiles(Map<String, StaticFile> staticFiles) {
        ConfiguredPathsProvider.staticFiles = staticFiles;
    }

    public static Map<String, StaticFile> staticFiles() {
        return staticFiles;
    }

    public static void setOutputTarget(String targetDir) {
        ConfiguredPathsProvider.targetDir = targetDir;
    }

    public static String targetDir() {
        return targetDir;
    }

    public static void setBuildSelectedPaths(RoqSelection selectedPaths) {
        buildSelectedPaths = selectedPaths;
    }

    @Produces
    @Singleton
    RoqSelection produce() {
        return buildSelectedPaths;
    }
}
