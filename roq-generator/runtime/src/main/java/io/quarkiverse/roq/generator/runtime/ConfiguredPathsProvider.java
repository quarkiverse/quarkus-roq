package io.quarkiverse.roq.generator.runtime;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Singleton
public class ConfiguredPathsProvider {

    private static volatile String targetDir;

    private static volatile RoqSelection buildSelectedPaths;
    private static final ConcurrentHashMap<String, StaticFile> staticFiles = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<SelectedPath> pathQueue = new ConcurrentLinkedQueue<>();

    public static void addStaticFiles(Map<String, StaticFile> files) {
        staticFiles.putAll(files);
    }

    public static void addStaticFile(String path, StaticFile file) {
        staticFiles.put(path, file);
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

    public static void enqueuePath(SelectedPath path) {
        pathQueue.add(path);
    }

    public static void enqueueAll(List<SelectedPath> paths) {
        pathQueue.addAll(paths);
    }

    public static ConcurrentLinkedQueue<SelectedPath> pathQueue() {
        return pathQueue;
    }

    @Produces
    @Singleton
    RoqSelection produce() {
        return buildSelectedPaths;
    }
}
