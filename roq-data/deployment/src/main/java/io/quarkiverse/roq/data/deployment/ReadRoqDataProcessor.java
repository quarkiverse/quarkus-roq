package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.vertx.core.json.JsonObject;

public class ReadRoqDataProcessor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".yaml", ".yml");

    RoqDataConfig roqDataConfig;

    @BuildStep
    void scanDataFiles(RoqProjectBuildItem roqProject, RoqDataConfig config, BuildProducer<RoqDataJsonBuildItem> dataProducer) {
        if (roqProject != null) {
            try {
                Set<RoqDataJsonBuildItem> items = scanDataFiles(roqProject, config);

                for (RoqDataJsonBuildItem item : items) {
                    dataProducer.produce(item);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

    }

    public Set<RoqDataJsonBuildItem> scanDataFiles(RoqProjectBuildItem roqProject, RoqDataConfig config) throws IOException {

        HashSet<RoqDataJsonBuildItem> items = new HashSet<>();

        roqProject.consumePathFromSite(config.dir(), (path) -> {
            if (Files.isDirectory(path)) {
                try (Stream<Path> pathStream = Files.find(path, Integer.MAX_VALUE,
                        (p, a) -> Files.isRegularFile(p) && isExtensionSupported(p))) {
                    pathStream.forEach(addRoqDataJsonBuildItem(path, items));
                } catch (IOException e) {
                    throw new RuntimeException("Error while scanning data files on location %s".formatted(path.toString()), e);
                }
            }
        });

        return items;
    }

    private static Consumer<Path> addRoqDataJsonBuildItem(Path rootDir, HashSet<RoqDataJsonBuildItem> items) {
        return file -> {
            var name = rootDir.relativize(file).toString().replaceAll("\\..*", "").replaceAll("\\/", "_");
            String filename = file.getFileName().toString();

            JsonObjectConverter.Extensions converter = JsonObjectConverter.findExtensionConverter(filename);

            if (converter != null) {
                try {
                    JsonObject jsonObject = converter.convert(Files.readString(file, StandardCharsets.UTF_8));
                    items.add(new RoqDataJsonBuildItem(name, jsonObject));
                } catch (IOException e) {
                    throw new RuntimeException("Was not possible to read the file %s using the converter for %s extension"
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
