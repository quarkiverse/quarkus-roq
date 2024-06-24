package io.quarkiverse.statiq.data.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.quarkiverse.statiq.data.deployment.items.StatiqDataJsonBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.runtime.util.ClassPathUtils;
import io.vertx.core.json.JsonObject;

public class ReadStatiqDataProcessor {

    private static final String META_INF_RESOURCES = "META-INF/resources";
    private static final Set<String> YAML_EXTENSIONS = Set.of(".yaml", ".yml");
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".yaml", ".yml");

    @BuildStep
    void scanDataFiles(BuildProducer<StatiqDataJsonBuildItem> dataProducer,
            StatiqDataConfig statiqDataConfig) {
        try {
            Set<StatiqDataJsonBuildItem> items = scanDataFiles(statiqDataConfig.location);

            for (StatiqDataJsonBuildItem item : items) {
                dataProducer.produce(item);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Set<StatiqDataJsonBuildItem> scanDataFiles(String location) throws IOException {

        HashSet<StatiqDataJsonBuildItem> items = new HashSet<>();

        String finalLocation = buildFinalLocation(location);

        ClassPathUtils.consumeAsPaths(finalLocation, (path) -> {
            if (Files.isDirectory(path)) {
                try (Stream<Path> pathStream = Files.walk(path)) {

                    pathStream
                            .filter(Files::isRegularFile)
                            .filter(isExtensionSupported())
                            .forEach(addStatiqDataJsonBuildItem(items));

                } catch (IOException e) {
                    throw new RuntimeException("Was not possible to scan data files on location %s".formatted(location), e);
                }
            }
        });

        return items;
    }

    private static Consumer<Path> addStatiqDataJsonBuildItem(HashSet<StatiqDataJsonBuildItem> items) {
        return file -> {
            String filename = file.getFileName().toString();

            JsonObjectConverter.Extensions converter = JsonObjectConverter.findExtensionConverter(filename);

            if (converter != null) {
                try {
                    JsonObject jsonObject = converter.convert(Files.readString(file, StandardCharsets.UTF_8));
                    items.add(new StatiqDataJsonBuildItem(filename, jsonObject));
                } catch (IOException e) {
                    throw new RuntimeException("Was not possible to read the file %s using the converter for %s extension"
                            .formatted(filename, converter.getExtension()), e);
                }
            }
        };
    }

    private static Predicate<Path> isExtensionSupported() {
        return file -> {
            String fileName = file.getFileName().toString();
            return SUPPORTED_EXTENSIONS.stream().anyMatch(fileName::endsWith);
        };
    }

    private String buildFinalLocation(String location) {
        while (location.startsWith("/")) {
            location = location.substring(1);
        }
        return "%s/%s".formatted(META_INF_RESOURCES, location);
    }
}
