package io.quarkiverse.roq.generator.deployment;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.*;
import java.util.function.Function;

import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.generator.runtime.*;
import io.quarkiverse.roq.generator.runtime.devui.RoqGeneratorJsonRPCService;
import io.quarkiverse.roq.util.PathUtils;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem;
import io.quarkus.vertx.http.runtime.GeneratedStaticResourcesRecorder;

class RoqGeneratorProcessor {

    private static final String FEATURE = "roq-generator";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    CardPageBuildItem create(CurateOutcomeBuildItem bi) {
        CardPageBuildItem pageBuildItem = new CardPageBuildItem();
        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .title("Roq Generator selection")
                .componentLink("qwc-roq-generator.js")
                .icon("font-awesome-solid:link"));

        return pageBuildItem;
    }

    @BuildStep
    JsonRPCProvidersBuildItem createJsonRPCServiceForCache() {
        return new JsonRPCProvidersBuildItem(RoqGeneratorJsonRPCService.class);
    }

    @BuildStep
    void produceBeans(BuildProducer<AdditionalBeanBuildItem> additionalBeanProducer) {
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(RoqGenerator.class));
        additionalBeanProducer.produce(AdditionalBeanBuildItem.unremovableOf(ConfiguredPathsProvider.class));
    }

    @BuildStep
    public BuildSelectionBuildItem initBuildSelection(
            RoqGeneratorConfig config,
            List<GeneratedStaticResourceBuildItem> generatedStaticResources,
            List<NotFoundPageDisplayableEndpointBuildItem> notFoundPageDisplayableEndpoints,
            List<SelectedPathBuildItem> selectedPaths,
            StaticResourcesBuildItem staticResourcesBuildItem) {
        Set<String> staticPaths = new HashSet<>();
        if (staticResourcesBuildItem != null) {
            staticPaths.addAll(staticResourcesBuildItem.getPaths().stream().map(PathUtils::prefixWithSlash).toList());
        }
        if (notFoundPageDisplayableEndpoints != null) {
            staticPaths.addAll(notFoundPageDisplayableEndpoints.stream()
                    .filter(not(NotFoundPageDisplayableEndpointBuildItem::isAbsolutePath))
                    .map(NotFoundPageDisplayableEndpointBuildItem::getEndpoint)
                    .map(PathUtils::prefixWithSlash)
                    .toList());
        }
        final Map<String, GeneratedStaticResourceBuildItem> generatedStaticResourcesMap = generatedStaticResources.stream()
                .collect(toMap(GeneratedStaticResourceBuildItem::getEndpoint, Function.identity()));
        final Map<String, String> selectedPathsFromBuildItems = selectedPaths.stream()
                .collect(toMap(SelectedPathBuildItem::path, SelectedPathBuildItem::outputPath));
        final Map<String, StaticFile> staticFiles = new HashMap<>();
        final RoqSelection buildSelectedPaths = getSelectedPaths(config, selectedPathsFromBuildItems,
                generatedStaticResourcesMap, staticPaths, staticFiles);
        return new BuildSelectionBuildItem(staticFiles, buildSelectedPaths);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void initStaticFiles(
            BuildSelectionBuildItem buildSelection,
            RoqGeneratorRecorder recorder) {
        recorder.setStaticFiles(buildSelection.staticFiles());
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void initSelection(
            BuildSelectionBuildItem buildSelection,
            OutputTargetBuildItem outputTarget,
            RoqGeneratorRecorder recorder) {
        recorder.setBuildSelectedPaths(buildSelection.selectedPaths());
        recorder.setOutputTarget(outputTarget.getOutputDirectory().toAbsolutePath().toString());
    }

    private RoqSelection getSelectedPaths(RoqGeneratorConfig config,
            Map<String, String> selectedPathsFromBuildItem,
            Map<String, GeneratedStaticResourceBuildItem> generatedStaticResourcesMap,
            Set<String> staticPaths,
            Map<String, StaticFile> staticFiles) {
        List<SelectedPath> selectedPaths = new ArrayList<>();
        for (var e : config.customPaths().entrySet()) {
            final String path = PathUtils.prefixWithSlash(e.getKey());
            addStaticFileIfPresent(generatedStaticResourcesMap, path, staticFiles);
            selectedPaths.add(SelectedPath.builder().path(path).outputPath(e.getValue())
                    .source(Origin.CONFIG).build());
        }
        for (String p : config.paths().orElse(List.of())) {
            if (!isGlobPattern(p) && p.startsWith("/")) {
                // fixed paths are directly added
                addStaticFileIfPresent(generatedStaticResourcesMap, p, staticFiles);
                selectedPaths.add(SelectedPath.builder().path(p).source(Origin.CONFIG).build());
                continue;
            }
            if (staticPaths != null) {
                // Try to detect fixed paths from glob pattern
                for (String staticPath : staticPaths) {
                    final String path = PathUtils.prefixWithSlash(staticPath);
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + p);
                    if (matcher.matches(Path.of(path))) {
                        addStaticFileIfPresent(generatedStaticResourcesMap, path, staticFiles);
                        selectedPaths.add(
                                SelectedPath.builder().source(Origin.CONFIG).path(path).build());
                    }
                }
            }

        }
        for (var e : selectedPathsFromBuildItem.entrySet()) {
            selectedPaths.add(SelectedPath.builder().source(Origin.BUILD_ITEM).path(e.getKey())
                    .outputPath(e.getValue()).build());
            addStaticFileIfPresent(generatedStaticResourcesMap, e.getKey(), staticFiles);
        }
        return new RoqSelection(selectedPaths);
    }

    private static void addStaticFileIfPresent(
            Map<String, GeneratedStaticResourceBuildItem> generatedStaticResourcesMap,
            String path,
            Map<String, StaticFile> staticFiles) {
        if (generatedStaticResourcesMap.containsKey(path)) {
            final GeneratedStaticResourceBuildItem generatedItem = generatedStaticResourcesMap.get(path);
            if (generatedItem.isFile() && generatedItem.getFile().getFileSystem().provider().getScheme().equals("file")) {
                staticFiles.put(path,
                        new StaticFile(generatedItem.getFileAbsolutePath(), StaticFile.FetchType.FILE));
            } else {
                staticFiles.put(path,
                        new StaticFile(
                                GeneratedStaticResourcesRecorder.META_INF_RESOURCES
                                        + generatedItem.getEndpoint(),
                                StaticFile.FetchType.CLASSPATH));
            }
        } else {
            final String resourceName;
            if (!"/".equals(path)) {
                resourceName = GeneratedStaticResourcesRecorder.META_INF_RESOURCES
                        + path;
            } else {
                resourceName = GeneratedStaticResourcesRecorder.META_INF_RESOURCES + "/index.html";
            }
            QuarkusClassLoader.visitRuntimeResources(resourceName, pathVisit -> {
                if (Files.isRegularFile(pathVisit.getPath())) {
                    staticFiles.put(path,
                            new StaticFile(
                                    resourceName,
                                    StaticFile.FetchType.CLASSPATH));
                }
            });
        }
    }

    private static boolean isGlobPattern(String s) {
        // Check if the string contains any glob pattern special characters
        return s.contains("*") || s.contains("{") || s.contains("}") || s.contains("[") || s.contains("]");
    }
}
