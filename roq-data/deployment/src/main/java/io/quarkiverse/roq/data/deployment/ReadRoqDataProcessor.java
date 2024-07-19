package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.Index;
import org.jboss.jandex.Type;
import org.jboss.logging.Logger;

import io.quarkiverse.roq.data.deployment.converters.JsonObjectConverter;
import io.quarkiverse.roq.data.deployment.items.ParentMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataMappingBuildItem;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.ApplicationIndexBuildItem;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public class ReadRoqDataProcessor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".yaml", ".yml");
    private static final String PARENT_TYPE_PARAMETER_NAME = "list";
    private static final Logger LOG = Logger.getLogger(ReadRoqDataProcessor.class);
    RoqDataConfig roqDataConfig;

    @BuildStep
    void scanDataFiles(RoqProjectBuildItem roqProject, RoqDataConfig config, BuildProducer<RoqDataJsonBuildItem> dataProducer,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer) {
        if (roqProject.isActive()) {
            try {
                Collection<RoqDataJsonBuildItem> items = scanDataFiles(roqProject, watchedFilesProducer, config);

                for (RoqDataJsonBuildItem item : items) {
                    dataProducer.produce(item);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @BuildStep
    void scanDataMappings(ApplicationIndexBuildItem indexBuildItem,
            BuildProducer<RoqDataMappingBuildItem> roqMappings, BuildProducer<ParentMappingBuildItem> parentMappings,
            List<RoqDataJsonBuildItem> roqDataJsonBuildItems,
            RoqDataConfig config) {
        Index jandex = indexBuildItem.getIndex();
        Collection<AnnotationInstance> annotations = jandex.getAnnotations(DataMapping.class);

        Map<String, Object> dataJsonMap = roqDataJsonBuildItems.stream()
                .collect(Collectors.toMap(RoqDataJsonBuildItem::getName, RoqDataJsonBuildItem::getData));

        Map<String, String> annotationMap = annotations.stream().collect(Collectors.toMap(
                annotation -> annotation.value().asString(), annotation -> annotation.target().asClass().name().toString()));

        List<String> errors = collectConfigErrors(annotationMap, dataJsonMap);

        if (config.enforceBean() && !errors.isEmpty()) {
            throw new IllegalStateException(
                    "The Roq data configuration is not valid. The data mapping and data files are not matching: %n%s"
                            .formatted(String.join(System.lineSeparator(), errors)));
        } else {
            errors.forEach(LOG::warn);
        }

        for (AnnotationInstance annotation : annotations) {

            AnnotationTarget target = annotation.target();
            String name = annotation.value().asString();

            if (!dataJsonMap.containsKey(name)) {
                continue;
            }

            final Optional<FieldInfo> listField = target.asClass().fields().stream()
                    .filter(this::isComplianceWithParentMapping)
                    .findAny();

            Object json = dataJsonMap.get(name);
            DotName className = target.asClass().name();

            if (listField.isPresent()) {
                final FieldInfo fieldInfo = listField.get();
                parentMappings.produce(new ParentMappingBuildItem(
                        className,
                        fieldInfo.type().asParameterizedType().arguments().get(0).name(),
                        json));
                continue;
            }

            final RoqDataMappingBuildItem roqMapping = new RoqDataMappingBuildItem(name, className, json,
                    target.asClass().isRecord());

            roqMappings.produce(roqMapping);
        }
    }

    private boolean isComplianceWithParentMapping(FieldInfo fieldInfo) {
        if (fieldInfo.type().kind().equals(Type.Kind.PARAMETERIZED_TYPE)) {
            return fieldInfo.name().equals(PARENT_TYPE_PARAMETER_NAME) &&
                    fieldInfo.type().asParameterizedType().name()
                            .equals(ClassType.create(List.class).name());
        }
        return false;
    }

    private List<String> collectConfigErrors(Map<String, String> annotationMap, Map<String, Object> dataJsonMap) {
        List<String> messages = new ArrayList<>();
        for (String name : annotationMap.keySet()) {
            if (!dataJsonMap.containsKey(name)) {
                messages.add("The @DataMapping#value('%s') does not match with any data file".formatted(name));
            }
        }
        for (String name : dataJsonMap.keySet()) {
            if (!annotationMap.containsKey(name)) {
                messages.add("The data file '%s' does not match with any @DataMapping class".formatted(name));
            }
        }
        return messages;
    }

    public Collection<RoqDataJsonBuildItem> scanDataFiles(RoqProjectBuildItem roqProject,
            BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer, RoqDataConfig config)
            throws IOException {

        Map<String, RoqDataJsonBuildItem> items = new HashMap<>();

        roqProject.consumePathFromSite(config.dir(), (path) -> {
            if (Files.isDirectory(path)) {
                try (Stream<Path> pathStream = Files.find(path, Integer.MAX_VALUE,
                        (p, a) -> Files.isRegularFile(p) && isExtensionSupported(p))) {
                    pathStream.forEach(addRoqDataJsonBuildItem(watchedFilesProducer, path, items));
                } catch (IOException e) {
                    throw new RuntimeException("Error while scanning data files on location %s".formatted(path.toString()), e);
                }
            }
        });

        return items.values();
    }

    private static Consumer<Path> addRoqDataJsonBuildItem(BuildProducer<HotDeploymentWatchedFileBuildItem> watchedFilesProducer,
            Path rootDir, Map<String, RoqDataJsonBuildItem> items) {
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
                    Object value = converter.convert(Files.readString(file, StandardCharsets.UTF_8));
                    items.put(name, new RoqDataJsonBuildItem(name, value));
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
