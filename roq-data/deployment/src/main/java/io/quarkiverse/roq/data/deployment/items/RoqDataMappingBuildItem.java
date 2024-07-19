package io.quarkiverse.roq.data.deployment.items;

import org.jboss.jandex.DotName;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqDataMappingBuildItem extends MultiBuildItem {

    private final String name;
    private final DotName className;
    private final Object data;
    private final boolean isRecord;

    public RoqDataMappingBuildItem(String name, DotName className, Object data, boolean isRecord) {
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
