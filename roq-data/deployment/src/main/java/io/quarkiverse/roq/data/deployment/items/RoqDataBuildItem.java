package io.quarkiverse.roq.data.deployment.items;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item representing a Roq data file.
 */
public final class RoqDataBuildItem extends MultiBuildItem {

    /**
     * The name of the Roq data file.
     */
    private final String name;

    /**
     * Represent the source file.
     */
    private final Path sourceFile;

    /**
     * The content of the Roq data file.
     */
    private final byte[] content;

    /**
     * The converter for this data
     */
    private final DataConverter converter;

    public RoqDataBuildItem(String name, Path sourceFile, byte[] content, DataConverter converter) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.content = content;
        this.converter = converter;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public String getName() {
        return name;
    }

    public byte[] getContent() {
        return content;
    }

    public Object convert() throws IOException {
        return this.converter.convert(content);
    }

    public DataConverter converter() {
        return converter;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        RoqDataBuildItem that = (RoqDataBuildItem) o;
        return Objects.equals(name, that.name) && Objects.equals(sourceFile, that.sourceFile)
                && Objects.deepEquals(content, that.content) && Objects.equals(converter, that.converter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sourceFile, Arrays.hashCode(content), converter);
    }
}
