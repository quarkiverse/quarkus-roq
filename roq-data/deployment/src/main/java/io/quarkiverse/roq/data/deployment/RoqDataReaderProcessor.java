package io.quarkiverse.roq.data.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.converters.DataConverterFinder;
import io.quarkiverse.roq.data.deployment.exception.DataConversionException;
import io.quarkiverse.roq.data.deployment.exception.DataMappingMismatchException;
import io.quarkiverse.roq.data.deployment.exception.DataMappingRequiredFileException;
import io.quarkiverse.roq.data.deployment.exception.DataScanningException;
import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectScannerLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public class RoqDataReaderProcessor {

    private static final String GLOB = "glob:**.{yaml,yml,json}";
    private static final Logger LOG = Logger.getLogger(RoqDataReaderProcessor.class);
    private static final DotName DATA_MAPPING_ANNOTATION = DotName.createSimple(DataMapping.class.getName());
    RoqDataConfig roqDataConfig;

    @BuildStep
    void scanDataFiles(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            RoqDataConfig config,
            RoqJacksonBuildItem jackson,
            BuildProducer<RoqDataBuildItem> dataProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer) {
        if (roqProject.isActive()) {
            DataConverterFinder converter = new DataConverterFinder(jackson.getJsonMapper(), jackson.getYamlMapper());
            try {
                Collection<RoqDataBuildItem> items = scanDataFiles(roqProject, scanner, converter, watchedFilesProducer,
                        config);

                for (RoqDataBuildItem item : items) {
                    dataProducer.produce(item);
                }

            } catch (IOException e) {
                throw new DataScanningException("Unable to scan data files", e);
            }
        }

    }

    @BuildStep
    void scanDataDir(BuildProducer<ProjectScannerLocalDirBuildItem> scanLocalDirProducer, RoqDataConfig dataConfig,
            RoqProjectBuildItem roqProject) {
        roqProject.addScannerForLocalRoqDir(scanLocalDirProducer, dataConfig.dir());
    }

    @BuildStep
    AdditionalIndexedClassesBuildItem addAnnotation() {
        return new AdditionalIndexedClassesBuildItem(DATA_MAPPING_ANNOTATION.toString());
    }

    @BuildStep
    void scanDataMappings(
            CombinedIndexBuildItem index,
            List<RoqDataBuildItem> roqDataBuildItems,
            BuildProducer<DataMappingBuildItem> dataMappingProducer,
            BuildProducer<RoqDataJsonBuildItem> dataJsonProducer,
            RoqDataConfig config) {
        Collection<AnnotationInstance> annotations = index.getIndex().getAnnotations(DATA_MAPPING_ANNOTATION);

        Map<String, RoqDataBuildItem> dataJsonMap = roqDataBuildItems.stream()
                .collect(Collectors.toMap(RoqDataBuildItem::getName, Function.identity()));

        Map<String, AnnotationInstance> annotationMap = annotations.stream().collect(Collectors.toMap(
                annotation -> annotation.value().asString(), Function.identity()));

        annotationMap.forEach((key, annotationInstance) -> {
            boolean isRequired = Optional.ofNullable(annotationInstance.value("required"))
                    .map(AnnotationValue::asBoolean)
                    .orElse(false);

            if (isRequired && !dataJsonMap.containsKey(key)) {
                throw new DataMappingRequiredFileException(
                        "The @DataMapping#value(%s) is required, but there is no corresponding data file".formatted(key));
            }
        });

        if (config.enforceBean()) {
            List<String> dataMappingErrors = collectDataMappingErrors(annotationMap.keySet(), dataJsonMap.keySet());
            if (!dataMappingErrors.isEmpty()) {
                throw new DataMappingMismatchException(
                        "Some data mappings and data files do not match: %n%s. Data mapping enforcement may be disabled in Roq."
                                .formatted(String.join(System.lineSeparator(), dataMappingErrors)));
            }
        }

        for (RoqDataBuildItem roqDataBuildItem : roqDataBuildItems) {
            String name = roqDataBuildItem.getName();
            if (annotationMap.containsKey(name)) {
                // Prepare mapping as typed bean
                AnnotationTarget target = annotationMap.get(name).target();
                if (!dataJsonMap.containsKey(name)) {
                    continue;
                }

                RoqDataBuildItem item = dataJsonMap.get(name);
                DotName className = target.asClass().name();
                // parent mapping
                final boolean isParentMapping = annotationMap.get(name)
                        .valueWithDefault(index.getIndex(), "parentArray").asBoolean();
                if (isParentMapping) {
                    final Optional<MethodInfo> parentMapping = target.asClass().constructors().stream()
                            .filter(this::isComplianceWithParentMapping)
                            .findAny();
                    final MethodInfo methodInfo = parentMapping.orElseThrow(() -> new RuntimeException(
                            "@DataMapping(parentArray=true) should declare a single parameter constructor with type List<T>"));
                    final DotName type = methodInfo.parameterType(0).asParameterizedType().arguments().get(0).name();
                    dataMappingProducer.produce(new DataMappingBuildItem(
                            name,
                            item.sourceFile(),
                            className,
                            type, // need to get dynamically
                            item.getContent(),
                            item.converter(), target.asClass().isRecord()));
                    continue;
                }

                final DataMappingBuildItem roqMapping = new DataMappingBuildItem(
                        name,
                        item.sourceFile(),

                        null,
                        className,
                        item.getContent(),
                        item.converter(),
                        target.asClass().isRecord());

                dataMappingProducer.produce(roqMapping);
            } else {
                // Prepare mapping as JsonObject or JsonArray (we convert here to avoid one more step)
                try {
                    final Object converted = roqDataBuildItem.converter().convert(roqDataBuildItem.getContent());
                    dataJsonProducer.produce(new RoqDataJsonBuildItem(name,
                            converted));
                } catch (IOException e) {
                    throw new DataConversionException(
                            "Unable to convert data file %s as an Object".formatted(roqDataBuildItem.sourceFile()), e);
                }
            }
        }

    }

    private boolean isComplianceWithParentMapping(MethodInfo methodInfo) {
        if (methodInfo.parametersCount() == 1) {
            return methodInfo.parameterType(0).asParameterizedType().name()
                    .equals(ClassType.create(List.class).name());
        }
        return false;
    }

    private List<String> collectDataMappingErrors(Set<String> annotations, Set<String> data) {
        List<String> messages = new ArrayList<>();

        for (String name : annotations) {
            if (!data.contains(name)) {
                messages.add("The @DataMapping#value('%s') does not match with any data file".formatted(name));
            }
        }
        for (String name : data) {
            if (!annotations.contains(name)) {
                messages.add("The data file '%s' does not match with any @DataMapping class".formatted(name));
            }
        }
        return messages;
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    void watch(RoqDataConfig config, RoqProjectBuildItem roqProject,
            BuildProducer<WebBundlerWatchedDirBuildItem> webBundlerWatch) {
        final Path localDataDir = roqProject.fromLocalRoqDir(config.dir());
        if (localDataDir == null) {
            return;
        }
        webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(localDataDir));
    }

    public Collection<RoqDataBuildItem> scanDataFiles(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            DataConverterFinder converter,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer,
            RoqDataConfig config)
            throws IOException {

        List<RoqDataBuildItem> items = new ArrayList<>();

        // Query 1: Local project files under data dir
        List<ProjectFile> localFiles = scanner.query()
                .scopeDir(config.dir())
                .origin(ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .matching(GLOB)
                .list();

        // Query 2: Classpath resources under roqResourceDir/data dir
        List<ProjectFile> resourceFiles = scanner.query()
                .scopeDir(roqProject.resolveRoqResourceSubDir(config.dir()))
                .origin(ProjectFile.Origin.APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .matching(GLOB)
                .list();

        final List<ProjectFile> files = ScanQueryBuilder.mergeByScopedPath(localFiles, resourceFiles);
        for (ProjectFile file : files) {
            var name = removeExtension(toUnixPath(file.scopedPath()));
            String watchPath = file.watchPath();
            if (watchPath != null) {
                watchedFilesProducer.produce(new HotDeploymentWatchedFileBuildItem(watchPath, true));
            }
            DataConverter dataConverter = converter.fromFileName(file.scopedPath());
            if (dataConverter != null) {
                items.add(new RoqDataBuildItem(name, file.path(), file.content(), dataConverter));
            }
        }
        return items;
    }

}
