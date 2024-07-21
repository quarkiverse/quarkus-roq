package io.quarkiverse.roq.data.deployment.items;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class NormalDataMappingBuildItem extends MultiBuildItem {

    /**
     * Represents the data file name without extension.
     */
    private final String name;

    /**
     * Represents the {@link DotName} as fully qualified name.
     */
    private final DotName className;

    /**
     * The data file content as {@link Object}.
     */
    private final Object data;

    /**
     * Whether is a Java record or not.
     */
    private final boolean isRecord;

    public NormalDataMappingBuildItem(String name, DotName className, Object data, boolean isRecord) {
        this.name = name;
        this.className = className;
        this.data = data;
        this.isRecord = isRecord;
    }

    public String getName() {
        return name;
    }

    public DotName getClassName() {
        return className;
    }

    public Object getData() {
        return this.data;
    }

    public boolean isRecord() {
        return isRecord;
    }

    @Override
    public String toString() {
        return "RoqDataMappingBuildItem{" +
                "name='" + name + '\'' +
                ", className='" + className + '\'' +
                ", data=" + data +
                ", isRecord=" + isRecord +
                '}';
    }
}
