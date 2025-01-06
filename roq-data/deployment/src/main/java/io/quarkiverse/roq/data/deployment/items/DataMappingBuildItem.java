package io.quarkiverse.roq.data.deployment.items;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;

import org.jboss.jandex.DotName;

import io.quarkiverse.roq.data.deployment.DataConverter;
import io.quarkus.builder.item.MultiBuildItem;

public final class DataMappingBuildItem extends MultiBuildItem {

    /**
     * Represents the data file name without extension.
     */
    private final String name;

    /**
     * Represent the source file.
     */
    private final Path sourceFile;

    /**
     * Represents the {@link DotName} of parent type.
     */
    private final DotName parentType;

    /**
     * Represents the {@link DotName} as fully qualified name.
     */
    private final DotName className;

    /**
     * The data file content as {@link byte[]}.
     */
    private final byte[] content;

    /**
     * The converter for this data
     */
    private final DataConverter converter;

    /**
     * Whether is a Java record or not.
     */
    private final boolean isRecord;

    public DataMappingBuildItem(String name, Path sourceFile, DotName parentType, DotName className, byte[] content,
            DataConverter converter,
            boolean isRecord) {
        this.name = name;
        this.sourceFile = sourceFile;
        this.parentType = parentType;
        this.className = className;
        this.content = content;
        this.converter = converter;
        this.isRecord = isRecord;
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name;
    }

    public DotName getClassName() {
        return className;
    }

    public DotName getParentType() {
        return parentType;
    }

    public boolean isParentType() {
        return parentType != null;
    }

    public boolean isRecord() {
        return isRecord;
    }

    public DataConverter getConverter() {
        return converter;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        DataMappingBuildItem that = (DataMappingBuildItem) o;
        return isRecord == that.isRecord && Objects.equals(name, that.name) && Objects.equals(sourceFile, that.sourceFile)
                && Objects.equals(parentType, that.parentType) && Objects.equals(className, that.className)
                && Objects.deepEquals(content, that.content) && Objects.equals(converter, that.converter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, sourceFile, parentType, className, Arrays.hashCode(content), converter, isRecord);
    }
}
