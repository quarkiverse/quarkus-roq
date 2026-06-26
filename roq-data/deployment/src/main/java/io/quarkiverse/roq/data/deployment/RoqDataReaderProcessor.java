package io.quarkiverse.roq.data.deployment;

import static io.quarkiverse.tools.stringpaths.StringPaths.addTrailingSlash;
import static io.quarkiverse.tools.stringpaths.StringPaths.removeExtension;
import static io.quarkiverse.tools.stringpaths.StringPaths.toUnixPath;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.MethodInfo;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.json.JsonMapper;

import io.quarkiverse.roq.data.deployment.converters.DataConverterFinder;
import io.quarkiverse.roq.data.deployment.converters.JsonConverter;
import io.quarkiverse.roq.data.deployment.exception.DataConversionException;
import io.quarkiverse.roq.data.deployment.exception.DataMappingMismatchException;
import io.quarkiverse.roq.data.deployment.exception.DataMappingRequiredFileException;
import io.quarkiverse.roq.data.deployment.exception.DataScanningException;
import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBeanBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkiverse.roq.exception.RoqException;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkiverse.tools.projectscanner.ProjectScannerBuildItem;
import io.quarkiverse.tools.projectscanner.ScanDeclarationBuildItem;
import io.quarkiverse.tools.projectscanner.ScanLocalDirBuildItem;
import io.quarkiverse.tools.projectscanner.ScanQueryBuilder;
import io.quarkiverse.web.bundler.spi.items.WebBundlerWatchedDirBuildItem;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.AdditionalIndexedClassesBuildItem;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;
import io.vertx.core.json.JsonObject;

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
            BuildProducer<RoqDataBuildItem> dataProducer) {
        if (roqProject.isActive()) {
            DataConverterFinder converter = new DataConverterFinder(jackson.getJsonMapper(), jackson.getYamlMapper());
            try {
                Collection<RoqDataBuildItem> items = scanDataFiles(roqProject, scanner, converter, config);

                for (RoqDataBuildItem item : items) {
                    dataProducer.produce(item);
                }

            } catch (IOException e) {
                throw new DataScanningException(
                        RoqException.builder("Unable to scan data files")
                                .hint("Check that the data/ directory exists and its files are valid YAML or JSON")
                                .cause(e));
            }
        }

    }

    @BuildStep
    void declareAndScanDataDir(RoqDataConfig dataConfig, RoqProjectBuildItem roqProject,
            BuildProducer<ScanDeclarationBuildItem> declarations,
            BuildProducer<ScanLocalDirBuildItem> scanLocalDirProducer) {
        declarations.produce(ScanDeclarationBuildItem.of(dataConfig.dir()));
        if (!roqProject.isRoqResourcesInRoot()) {
            declarations.produce(ScanDeclarationBuildItem.of(roqProject.resolveRoqResourceSubDir(dataConfig.dir())));
        }
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
            RoqJacksonBuildItem jackson,
            BuildProducer<DataMappingBuildItem> dataMappingProducer,
            BuildProducer<RoqDataJsonBuildItem> dataJsonProducer,
            BuildProducer<RoqDataBeanBuildItem> dataBeanProducer,
            RoqDataConfig config) {
        Collection<AnnotationInstance> annotations = index.getIndex().getAnnotations(DATA_MAPPING_ANNOTATION);

        Map<String, RoqDataBuildItem> dataJsonMap = roqDataBuildItems.stream()
                .collect(Collectors.toMap(RoqDataBuildItem::getName, Function.identity()));

        Map<String, AnnotationInstance> annotationMap = annotations.stream().collect(Collectors.toMap(
                annotation -> annotation.value().asString(), Function.identity()));

        Map<String, DataMapping.Type> resolvedTypes = resolveAnnotationTypes(annotationMap);

        Set<String> dirAnnotationNames = resolvedTypes.entrySet().stream()
                .filter(e -> e.getValue() == DataMapping.Type.ARRAY_DIR || e.getValue() == DataMapping.Type.OBJECT_DIR)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        validateDataMappings(annotationMap, dirAnnotationNames, roqDataBuildItems, dataJsonMap, config);

        Map<String, TreeMap<String, RoqDataBuildItem>> allDirFiles = collectDirectoryFiles(roqDataBuildItems);
        Set<String> producedJsonNames = new HashSet<>();
        Map<String, Object> convertedData = new HashMap<>();

        for (RoqDataBuildItem roqDataBuildItem : roqDataBuildItems) {
            String fullName = roqDataBuildItem.getName();
            String derivedName = getCollectionName(fullName);
            if (annotationMap.containsKey(derivedName)) {
                DataMapping.Type type = resolvedTypes.get(derivedName);
                if (type == DataMapping.Type.ARRAY_DIR || type == DataMapping.Type.OBJECT_DIR) {
                    // Directory annotations are handled after the loop
                    continue;
                }

                AnnotationTarget target = annotationMap.get(derivedName).target();
                if (!dataJsonMap.containsKey(derivedName)) {
                    continue;
                }

                RoqDataBuildItem item = dataJsonMap.get(derivedName);
                DotName className = target.asClass().name();

                if (type == DataMapping.Type.ARRAY_FILE) {
                    final Optional<MethodInfo> parentMapping = target.asClass().constructors().stream()
                            .filter(this::isCompliantWithListConstructor)
                            .findAny();
                    final MethodInfo methodInfo = parentMapping.orElseThrow(() -> new RuntimeException(
                            "@DataMapping(type=ARRAY_FILE) should declare a constructor with a List<T> parameter"));
                    final DotName itemType = methodInfo.parameterType(0).asParameterizedType().arguments().get(0).name();
                    dataMappingProducer.produce(new DataMappingBuildItem(
                            fullName, item.sourceFile(), className, itemType,
                            item.getContent(), item.converter(), target.asClass().isRecord(), type));
                } else {
                    dataMappingProducer.produce(new DataMappingBuildItem(
                            fullName, item.sourceFile(), null, className,
                            item.getContent(), item.converter(), target.asClass().isRecord(), null));
                }
            } else {
                try {
                    final Object converted = roqDataBuildItem.converter().convert(roqDataBuildItem.getContent());
                    dataJsonProducer.produce(new RoqDataJsonBuildItem(fullName, converted));
                    producedJsonNames.add(fullName);
                    convertedData.put(fullName, converted);
                } catch (IOException e) {
                    throw new DataConversionException(
                            RoqException.builder("Unable to convert data file")
                                    .detail("Could not convert file %s as an Object"
                                            .formatted(roqDataBuildItem.sourceFile()))
                                    .sourceFilePath(roqDataBuildItem.sourceFile().toString())
                                    .hint("Verify the file contains valid YAML or JSON")
                                    .cause(e));
                }
            }
        }

        // Produce grouped RoqDataJsonBuildItem for directories (including deeply nested)
        Map<String, TreeMap<String, Object>> topDirs = new TreeMap<>();
        for (Map.Entry<String, Object> e : convertedData.entrySet()) {
            String name = e.getKey();
            int firstSlash = name.indexOf('/');
            if (firstSlash > 0) {
                String topDir = name.substring(0, firstSlash);
                String rest = name.substring(firstSlash + 1);
                topDirs.computeIfAbsent(topDir, k -> new TreeMap<>()).put(rest, e.getValue());
            }
        }
        for (Map.Entry<String, TreeMap<String, Object>> e : topDirs.entrySet()) {
            String dirName = e.getKey();
            if (!producedJsonNames.contains(dirName) && !dirAnnotationNames.contains(dirName)) {
                dataJsonProducer.produce(new RoqDataJsonBuildItem(dirName, buildNestedJsonObject(e.getValue())));
            }
        }

        // Handle typed directory annotations (ARRAY_DIR / OBJECT_DIR)
        processDirectoryAnnotations(allDirFiles, annotationMap, resolvedTypes, dirAnnotationNames, dataMappingProducer,
                jackson.getJsonMapper());
    }

    private static String getCollectionName(String fullName) {
        int indexOfSlash = fullName.indexOf("/");
        if (indexOfSlash == -1) {
            return fullName;
        }
        return fullName.substring(0, indexOfSlash);
    }

    private static Map<String, DataMapping.Type> resolveAnnotationTypes(Map<String, AnnotationInstance> annotationMap) {
        Map<String, DataMapping.Type> resolvedTypes = new HashMap<>();
        annotationMap.forEach((key, ann) -> {
            DataMapping.Type type = Optional.ofNullable(ann.value("type"))
                    .map(v -> DataMapping.Type.valueOf(v.asEnum()))
                    .orElse(DataMapping.Type.OBJECT_FILE);
            if (type == DataMapping.Type.OBJECT_FILE) {
                boolean parentArray = Optional.ofNullable(ann.value("parentArray"))
                        .map(AnnotationValue::asBoolean).orElse(false);
                if (parentArray) {
                    type = DataMapping.Type.ARRAY_FILE;
                }
            }
            resolvedTypes.put(key, type);
        });
        return resolvedTypes;
    }

    private static void validateDataMappings(
            Map<String, AnnotationInstance> annotationMap,
            Set<String> dirAnnotationNames,
            List<RoqDataBuildItem> roqDataBuildItems,
            Map<String, RoqDataBuildItem> dataJsonMap,
            RoqDataConfig config) {
        annotationMap.forEach((key, annotationInstance) -> {
            boolean isRequired = Optional.ofNullable(annotationInstance.value("required"))
                    .map(AnnotationValue::asBoolean)
                    .orElse(false);
            if (!isRequired) {
                return;
            }
            if (dirAnnotationNames.contains(key)) {
                boolean hasFiles = roqDataBuildItems.stream()
                        .anyMatch(item -> item.getName().startsWith(key + "/"));
                if (!hasFiles) {
                    throw new DataMappingRequiredFileException(
                            RoqException.builder("Required data directory not found")
                                    .detail("@DataMapping(\"%s\") is marked as required, but no data files found under '%s/'"
                                            .formatted(key, key))
                                    .hint("Add data files in the '%s/' directory".formatted(key)));
                }
            } else if (!dataJsonMap.containsKey(key)) {
                throw new DataMappingRequiredFileException(
                        RoqException.builder("Required data file not found")
                                .detail("@DataMapping(\"%s\") is marked as required, but no corresponding data file exists"
                                        .formatted(key))
                                .hint("Add a data file named '%s.yml' (or .json) in the data/ directory".formatted(key)));
            }
        });

        if (config.enforceBean()) {
            List<String> dataMappingErrors = collectDataMappingErrors(annotationMap.keySet(), dataJsonMap.keySet());
            if (!dataMappingErrors.isEmpty()) {
                throw new DataMappingMismatchException(
                        RoqException.builder("Data mapping mismatch")
                                .detail("Some data mappings and data files do not match:\n%s"
                                        .formatted(String.join(System.lineSeparator(), dataMappingErrors)))
                                .hint("Data mapping enforcement may be disabled in Roq configuration"));
            }
        }
    }

    private void processDirectoryAnnotations(
            Map<String, TreeMap<String, RoqDataBuildItem>> allDirFiles,
            Map<String, AnnotationInstance> annotationMap,
            Map<String, DataMapping.Type> resolvedTypes,
            Set<String> dirAnnotationNames,
            BuildProducer<DataMappingBuildItem> dataMappingProducer,
            JsonMapper jsonMapper) {

        for (String dirName : dirAnnotationNames) {
            AnnotationInstance ann = annotationMap.get(dirName);
            DataMapping.Type type = resolvedTypes.get(dirName);
            AnnotationTarget target = ann.target();
            DotName parentClassName = target.asClass().name();

            TreeMap<String, RoqDataBuildItem> dirFiles = allDirFiles.getOrDefault(dirName, new TreeMap<>());
            if (dirFiles.isEmpty()) {
                continue;
            }

            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                Class<?> parentClass = Class.forName(parentClassName.toString(), false, cl);

                if (type == DataMapping.Type.ARRAY_DIR) {
                    MethodInfo methodInfo = target.asClass().constructors().stream()
                            .filter(this::isCompliantWithListConstructor)
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException(
                                    "@DataMapping(type=ARRAY_DIR) on '%s' should declare a constructor with a List<T> parameter"
                                            .formatted(parentClassName)));
                    DotName itemTypeName = methodInfo.parameterType(0).asParameterizedType().arguments().getFirst().name();
                    Class<?> itemClass = Class.forName(itemTypeName.toString(), false, cl);

                    List<Object> items = new ArrayList<>();
                    for (RoqDataBuildItem fileItem : dirFiles.values()) {
                        //Concatenate YAML
                        items.add(fileItem.converter().convertToType(fileItem.getContent(), itemClass));
                    }

                    dataMappingProducer.produce(new DataMappingBuildItem(
                            dirName, Path.of(dirName), DotName.createSimple(parentClass), itemTypeName,
                            jsonMapper.writeValueAsBytes(items), new JsonConverter(jsonMapper), target.asClass().isRecord(),
                            type));

                } else {
                    MethodInfo methodInfo = target.asClass().constructors().stream()
                            .filter(this::isCompliantWithMapConstructor)
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException(
                                    "@DataMapping(type=OBJECT_DIR) on '%s' should declare a constructor with a Map<String, T> parameter"
                                            .formatted(parentClassName)));
                    DotName itemTypeName = methodInfo.parameterType(0).asParameterizedType().arguments().get(1).name();
                    Class<?> itemClass = Class.forName(itemTypeName.toString(), false, cl);

                    Map<String, Object> items = new TreeMap<>();
                    for (Map.Entry<String, RoqDataBuildItem> fileEntry : dirFiles.entrySet()) {
                        RoqDataBuildItem fileItem = fileEntry.getValue();
                        items.put(fileEntry.getKey(),
                                fileItem.converter().convertToType(fileItem.getContent(), itemClass));
                    }

                    dataMappingProducer.produce(new DataMappingBuildItem(
                            dirName, Path.of(dirName), DotName.createSimple(parentClass), itemTypeName,
                            jsonMapper.writeValueAsBytes(items), new JsonConverter(jsonMapper), target.asClass().isRecord(),
                            type));
                }
            } catch (ClassNotFoundException | IOException e) {
                throw new RuntimeException(
                        "Failed to process @DataMapping(type=%s) for directory '%s'".formatted(type, dirName), e);
            }
        }
    }

    private static JsonObject buildNestedJsonObject(Map<String, Object> flatMap) {
        Map<String, Object> result = new TreeMap<>();
        Map<String, TreeMap<String, Object>> subDirs = new TreeMap<>();

        for (Map.Entry<String, Object> entry : flatMap.entrySet()) {
            String key = entry.getKey();
            int slashIdx = key.indexOf('/');
            if (slashIdx > 0) {
                String dir = key.substring(0, slashIdx);
                String rest = key.substring(slashIdx + 1);
                subDirs.computeIfAbsent(dir, k -> new TreeMap<>()).put(rest, entry.getValue());
            } else {
                result.put(key, entry.getValue());
            }
        }

        for (Map.Entry<String, TreeMap<String, Object>> subDir : subDirs.entrySet()) {
            result.put(subDir.getKey(), buildNestedJsonObject(subDir.getValue()));
        }

        return new JsonObject(result);
    }

    private static Map<String, TreeMap<String, RoqDataBuildItem>> collectDirectoryFiles(
            List<RoqDataBuildItem> items) {
        Map<String, TreeMap<String, RoqDataBuildItem>> result = new HashMap<>();
        for (RoqDataBuildItem item : items) {
            String name = item.getName();
            int slashIdx = name.indexOf('/');
            if (slashIdx > 0) {
                if (name.lastIndexOf('/') == slashIdx) {
                    String dirName = name.substring(0, slashIdx);
                    String fileKey = name.substring(slashIdx + 1);
                    result.computeIfAbsent(dirName, k -> new TreeMap<>()).put(fileKey, item);
                }
            }
        }
        return result;
    }

    private boolean isCompliantWithListConstructor(MethodInfo methodInfo) {
        if (methodInfo.parametersCount() == 1) {
            return methodInfo.parameterType(0).asParameterizedType().name()
                    .equals(ClassType.create(List.class).name());
        }
        return false;
    }

    private boolean isCompliantWithMapConstructor(MethodInfo methodInfo) {
        if (methodInfo.parametersCount() == 1) {
            return methodInfo.parameterType(0).asParameterizedType().name()
                    .equals(ClassType.create(Map.class).name());
        }
        return false;
    }

    private static List<String> collectDataMappingErrors(Set<String> annotations, Set<String> data) {
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
            BuildProducer<WebBundlerWatchedDirBuildItem> webBundlerWatch,
            BuildProducer<HotDeploymentWatchedFileBuildItem> hotWatch) {
        final Path localDataDir = roqProject.fromLocalRoqDir(config.dir());
        if (localDataDir != null) {
            webBundlerWatch.produce(new WebBundlerWatchedDirBuildItem(localDataDir));
            RoqProjectBuildItem.watchDirRecursively(localDataDir, hotWatch);
        }
        String prefix = addTrailingSlash(roqProject.resolveRoqResourceSubDir(config.dir()));
        hotWatch.produce(HotDeploymentWatchedFileBuildItem.builder()
                .setLocationPredicate(p -> p.startsWith(prefix))
                .build());
    }

    public Collection<RoqDataBuildItem> scanDataFiles(RoqProjectBuildItem roqProject,
            ProjectScannerBuildItem scanner,
            DataConverterFinder converter,
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
                .origin(ProjectFile.Origin.ROOT_APPLICATION_RESOURCE, ProjectFile.Origin.DEPENDENCY_RESOURCE)
                .matching(GLOB)
                .list();

        final List<ProjectFile> files = ScanQueryBuilder.mergeByScopedPath(localFiles, resourceFiles);
        for (ProjectFile file : files) {
            var name = removeExtension(toUnixPath(file.scopedPath()));
            DataConverter dataConverter = converter.fromFileName(file.scopedPath());
            if (dataConverter != null) {
                items.add(new RoqDataBuildItem(name, file.file(), file.content(), dataConverter));
            }
        }
        return items;
    }

}
