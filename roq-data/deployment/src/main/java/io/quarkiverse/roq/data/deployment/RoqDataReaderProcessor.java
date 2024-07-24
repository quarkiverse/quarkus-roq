package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.converters.JsonObjectConverter;
import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public class RoqDataReaderProcessor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".yaml", ".yml");
    private static final String PARENT_TYPE_PARAMETER_NAME = "list";
    private static final Logger LOG = Logger.getLogger(RoqDataReaderProcessor.class);
    static DotName DATA_MAPPING_ANNOTATION = DotName.createSimple(DataMapping.class.getName());
    RoqDataConfig roqDataConfig;

    @BuildStep
    void scanDataFiles(RoqProjectBuildItem roqProject, RoqDataConfig config, BuildProducer<RoqDataBuildItem> dataProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer) {
        if (roqProject.isActive()) {
            try {
                Collection<RoqDataBuildItem> items = scanDataFiles(roqProject, watchedFilesProducer, config);

                for (RoqDataBuildItem item : items) {
                    dataProducer.produce(item);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @BuildStep
    void scanDataMappings(ApplicationIndexBuildItem indexBuildItem,
            List<RoqDataBuildItem> roqDataBuildItems,
            BuildProducer<DataMappingBuildItem> dataMappingProducer,
            BuildProducer<RoqDataJsonBuildItem> dataJsonProducer,
            RoqDataConfig config) {
        Collection<AnnotationInstance> annotations = indexBuildItem.getIndex().getAnnotations(DATA_MAPPING_ANNOTATION);

        Map<String, RoqDataBuildItem> dataJsonMap = roqDataBuildItems.stream()
                .collect(Collectors.toMap(RoqDataBuildItem::getName, Function.identity()));

        Map<String, AnnotationInstance> annotationMap = annotations.stream().collect(Collectors.toMap(
                annotation -> annotation.value().asString(), Function.identity()));

        List<String> errors = collectConfigErrors(annotationMap.keySet(), dataJsonMap.keySet());

        if (config.enforceBean() && !errors.isEmpty()) {
            throw new IllegalStateException(
                    "The Roq data configuration is not valid. The data mapping and data files are not matching: %n%s"
                            .formatted(String.join(System.lineSeparator(), errors)));
        } else {
            errors.forEach(LOG::warn);
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
                final Optional<MethodInfo> parentMapping = target.asClass().constructors().stream()
                        .filter(this::isComplianceWithParentMapping)
                        .findAny();
                if (parentMapping.isPresent()) {
                    final MethodInfo methodInfo = parentMapping.get();
                    final DotName type = methodInfo.parameterType(0).asParameterizedType().arguments().get(0).name();
                    dataMappingProducer.produce(new DataMappingBuildItem(
                            name,
                            className,
                            type, // need to get dynamically
                            item.getContent(),
                            item.converter(), target.asClass().isRecord()));
                    continue;
                }

                final DataMappingBuildItem roqMapping = new DataMappingBuildItem(
                        name,
                        null,
                        className,
                        item.getContent(),
                        item.converter(),
                        target.asClass().isRecord());

                dataMappingProducer.produce(roqMapping);
            } else {
                // Prepare mapping as JsonObject or JsonArray (we convert here to avoid one more step)
                try {
                    dataJsonProducer.produce(new RoqDataJsonBuildItem(name,
                            roqDataBuildItem.converter().convert(roqDataBuildItem.getContent())));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

    }

    private boolean isComplianceWithParentMapping(MethodInfo methodInfo) {
        if (methodInfo.parametersCount() == 1 && methodInfo.parameters().get(0).hasAnnotation(DataMapping.ParentArray.class)) {
            return methodInfo.parameterType(0).asParameterizedType().name()
                    .equals(ClassType.create(List.class).name());
        }
        return false;
    }

    private List<String> collectConfigErrors(Set<String> annotations, Set<String> data) {
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

    public Collection<RoqDataBuildItem> scanDataFiles(RoqProjectBuildItem roqProject,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer, RoqDataConfig config)
            throws IOException {

        Map<String, RoqDataBuildItem> items = new HashMap<>();

        roqProject.consumePathFromSite(config.dir(), (path) -> {
            if (Files.isDirectory(path)) {
                try (Stream<Path> pathStream = Files.find(path, Integer.MAX_VALUE,
                        (p, a) -> Files.isRegularFile(p) && isExtensionSupported(p))) {
                    pathStream.forEach(addRoqDataBuildItem(watchedFilesProducer, path, items));
                } catch (IOException e) {
                    throw new RuntimeException("Error while scanning data files on location %s".formatted(path.toString()), e);
                }
            }
        });

        return items.values();
    }

    private static Consumer<Path> addRoqDataBuildItem(BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer,
            Path rootDir, Map<String, RoqDataBuildItem> items) {
        return file -> {
            var name = rootDir.relativize(file).toString().replaceAll("\\..*", "").replaceAll("/", "_");
            if (items.containsKey(name)) {
                throw new RuntimeException("Multiple data files found for name: " + name);
            }
            String filename = file.getFileName().toString();
            if (Path.of("").getFileSystem().equals(file.getFileSystem())) {
                // We don't need to watch file out of the local filesystem
                watchedFilesProducer.produce(new HotDeploymentWatchedFileBuildItem(file.toAbsolutePath().toString(), true));
            }
            JsonObjectConverter.Extensions converter = JsonObjectConverter.findExtensionConverter(filename);

            if (converter != null) {
                try {
                    items.put(name, new RoqDataBuildItem(name, Files.readAllBytes(file), converter.converter()));
                } catch (IOException e) {
                    throw new UncheckedIOException("Error while decoding using %s converter: %s "
                            .formatted(filename, converter.getExtension()), e);
                }
            }
        };
    }

    private static boolean isExtensionSupported(Path file) {
        String fileName = file.getFileName().toString();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }

}
