package io.quarkiverse.roq.generator.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.prefixWithSlash;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toMap;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.LargeRecords;
import io.quarkiverse.roq.deployment.items.LargeRecordBuildItem;
import io.quarkiverse.roq.generator.deployment.items.SelectedPathBuildItem;
import io.quarkiverse.roq.generator.runtime.ConfiguredPathsProvider;
import io.quarkiverse.roq.generator.runtime.Origin;
import io.quarkiverse.roq.generator.runtime.RoqGenerator;
import io.quarkiverse.roq.generator.runtime.RoqGeneratorConfig;
import io.quarkiverse.roq.generator.runtime.RoqGeneratorMain;
import io.quarkiverse.roq.generator.runtime.RoqGeneratorRecorder;
import io.quarkiverse.roq.generator.runtime.RoqSelection;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkiverse.roq.generator.runtime.StaticFile;
import io.quarkiverse.roq.generator.runtime.devui.RoqGeneratorJsonRPCService;
import io.quarkiverse.tools.stringpaths.StringPaths;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.QuarkusApplicationClassBuildItem;
import io.quarkus.deployment.pkg.PackageConfig;
import io.quarkus.deployment.pkg.builditem.CurateOutcomeBuildItem;
import io.quarkus.deployment.pkg.builditem.OutputTargetBuildItem;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;
import io.quarkus.runtime.annotations.QuarkusMain;
import io.quarkus.vertx.http.deployment.devmode.NotFoundPageDisplayableEndpointBuildItem;
import io.quarkus.vertx.http.deployment.spi.GeneratedStaticResourceBuildItem;
import io.quarkus.vertx.http.deployment.spi.StaticResourcesBuildItem;
import io.quarkus.vertx.http.runtime.GeneratedStaticResourcesRecorder;

class RoqGeneratorProcessor {

    private static final Logger LOG = Logger.getLogger(RoqGeneratorProcessor.class);
    private static final DotName QUARKUS_MAIN = DotName.createSimple(QuarkusMain.class.getName());
    private static final String FEATURE = "roq-generator";
    private static final String SELECTION_RESOURCE = "roq-recorded/generator-selection.ser";
    private static final String STATIC_FILES_RESOURCE = "roq-recorded/generator-static-files.ser";

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

    /**
     * In batch mode, register {@link RoqGeneratorMain} as the application entry point so that the site is generated
     * once the application has started, and the application exits when done. This is skipped in dev mode, where the
     * application must stay up until it is stopped.
     */
    @BuildStep(onlyIfNot = IsDevelopment.class)
    void batchMain(RoqGeneratorConfig config,
            CombinedIndexBuildItem index,
            PackageConfig packageConfig,
            BuildProducer<QuarkusApplicationClassBuildItem> quarkusApplicationClass,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {
        if (!config.batch()) {
            return;
        }
        // Same precedence as Quarkus' MainClassBuildStep: an explicit main class, then an unnamed @QuarkusMain
        final Optional<String> otherMain = packageConfig.mainClass()
                .or(() -> index.getIndex().getAnnotations(QUARKUS_MAIN).stream()
                        .filter(a -> a.value("name") == null || a.value("name").asString().isEmpty())
                        .map(a -> a.target().asClass().name().toString())
                        .findFirst());
        if (otherMain.isPresent()) {
            LOG.warnf("'quarkus.roq.generator.batch' is enabled but the application provides its own main class (%s), "
                    + "which takes precedence: the static site will not be generated on startup. "
                    + "Inject RoqGenerator and call generateBlocking() from your main to generate it.",
                    otherMain.get());
            return;
        }
        additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(RoqGeneratorMain.class));
        quarkusApplicationClass.produce(new QuarkusApplicationClassBuildItem(RoqGeneratorMain.class));
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
            staticPaths.addAll(staticResourcesBuildItem.getPaths().stream().map(StringPaths::prefixWithSlash).toList());
        }
        if (notFoundPageDisplayableEndpoints != null) {
            staticPaths.addAll(notFoundPageDisplayableEndpoints.stream()
                    .filter(not(NotFoundPageDisplayableEndpointBuildItem::isAbsolutePath))
                    .map(NotFoundPageDisplayableEndpointBuildItem::getEndpoint)
                    .map(StringPaths::prefixWithSlash)
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
            BuildProducer<LargeRecordBuildItem> largeRecords,
            RoqGeneratorRecorder recorder) {
        // Large sites have tens of thousands of entries: recorded, they would exceed the
        // constant pool of one generated class.
        largeRecords.produce(new LargeRecordBuildItem(STATIC_FILES_RESOURCE,
                LargeRecords.serialize(new HashMap<>(buildSelection.staticFiles())),
                StaticFile.class, StaticFile.FetchType.class));
        recorder.setStaticFiles(STATIC_FILES_RESOURCE);
    }

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void initSelection(
            BuildSelectionBuildItem buildSelection,
            OutputTargetBuildItem outputTarget,
            BuildProducer<LargeRecordBuildItem> largeRecords,
            RoqGeneratorRecorder recorder) {
        RoqSelection selection = new RoqSelection(new ArrayList<>(buildSelection.selectedPaths().paths()));
        largeRecords.produce(new LargeRecordBuildItem(SELECTION_RESOURCE, LargeRecords.serialize(selection),
                RoqSelection.class, SelectedPath.class, Origin.class));
        recorder.setBuildSelectedPaths(SELECTION_RESOURCE);
        recorder.setOutputTarget(outputTarget.getOutputDirectory().toAbsolutePath().toString());
    }

    private RoqSelection getSelectedPaths(RoqGeneratorConfig config,
            Map<String, String> selectedPathsFromBuildItem,
            Map<String, GeneratedStaticResourceBuildItem> generatedStaticResourcesMap,
            Set<String> staticPaths,
            Map<String, StaticFile> staticFiles) {
        List<SelectedPath> selectedPaths = new ArrayList<>();
        for (var e : config.customPaths().entrySet()) {
            final String path = prefixWithSlash(e.getKey());
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
                    final String path = prefixWithSlash(staticPath);
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
