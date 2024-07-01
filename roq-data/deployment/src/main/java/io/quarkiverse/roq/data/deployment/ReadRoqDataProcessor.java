package io.quarkiverse.roq.data.deployment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.quarkiverse.roq.data.deployment.items.RoqDataJsonBuildItem;
import io.quarkiverse.roq.deployment.items.RoqProjectBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.HotDeploymentWatchedFileBuildItem;

public class ReadRoqDataProcessor {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(".json", ".yaml", ".yml");

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
