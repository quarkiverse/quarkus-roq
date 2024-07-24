package io.quarkiverse.roq.data.deployment.items;

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

    public DataMappingBuildItem(String name, DotName parentType, DotName className, byte[] content, DataConverter converter,
            boolean isRecord) {
        this.name = name;
        this.parentType = parentType;
        this.className = className;
        this.content = content;
        this.converter = converter;
        this.isRecord = isRecord;
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
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        DataMappingBuildItem that = (DataMappingBuildItem) object;
        return isRecord == that.isRecord && Objects.equals(name, that.name) && Objects.equals(className, that.className)
                && Arrays.equals(content, that.content) && Objects.equals(converter, that.converter);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, className, converter, isRecord);
        result = 31 * result + Arrays.hashCode(content);
        return result;
    }
}
