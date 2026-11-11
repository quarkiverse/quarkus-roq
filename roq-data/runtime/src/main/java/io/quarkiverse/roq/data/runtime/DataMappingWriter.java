package io.quarkiverse.roq.data.runtime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkus.arc.ClientProxy;

/**
 * Writes {@link DataMapping} beans back to their data file, preserving comments and layout of the original file (see
 * {@link DataSerializer}).
 * <p>
 * Inject it to save a bean, or a new instance of a mapped type:
 *
 * <pre>
 * &#64;Inject
 * DataMappingWriter writer;
 *
 * writer.save(new Settings("New title", settings.links()));
 * </pre>
 *
 * In dev mode, saving a data file triggers a live reload, after which the injected beans reflect the new data.
 * <p>
 * Only {@link DataMapping.Type#OBJECT_FILE} and {@link DataMapping.Type#ARRAY_FILE} mappings are supported.
 */
public class DataMappingWriter {

    private final Map<String, String> sourceFiles;
    private final DataSerializer serializer;

    /**
     * @param sourceFiles the data source file path by mapped class name
     */
    public DataMappingWriter(Map<String, String> sourceFiles, DataSerializer serializer) {
        this.sourceFiles = Map.copyOf(sourceFiles);
        this.serializer = serializer;
    }

    /**
     * Save the data to the file it was loaded from.
     *
     * @param data a {@link DataMapping} bean or instance
     * @return the written file
     * @throws IllegalArgumentException if the data type has no writable source file
     * @throws IllegalStateException if the source file no longer exists (e.g. the application runs away from the
     *         project it was built from)
     */
    public Path save(Object data) {
        final Class<?> type = ClientProxy.unwrap(data).getClass();
        final String source = sourceFiles.get(type.getName());
        if (source == null) {
            throw new IllegalArgumentException(
                    "No writable data file found for %s. Only @DataMapping (OBJECT_FILE and ARRAY_FILE) types loaded from a local file can be saved, use save(data, path) otherwise."
                            .formatted(type.getName()));
        }
        final Path file = Path.of(source);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException(
                    "The data file %s of %s no longer exists. Use save(data, path) to write to another file."
                            .formatted(file, type.getName()));
        }
        return save(data, file);
    }

    /**
     * Save the data to the given file. If the file exists, it is updated preserving its comments and layout, otherwise
     * it is created.
     *
     * @param data the data
     * @param file the target file, the format is resolved from its extension
     * @return the written file
     */
    public synchronized Path save(Object data, Path file) {
        final DataFormat format = DataFormat.fromFileName(file.getFileName().toString())
                .orElseThrow(() -> new IllegalArgumentException("Unsupported data file extension: " + file));
        try {
            final String original = Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : null;
            final String updated = serializer.write(data, original, format);
            if (!updated.equals(original)) {
                if (file.getParent() != null) {
                    Files.createDirectories(file.getParent());
                }
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            }
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to write data file " + file, e);
        }
    }
}
