package io.quarkiverse.roq.data.deployment.items;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

public final class RoqDataBeanBuildItem extends MultiBuildItem {

    /**
     * Represents the data file name without extension.
     */
    private final String name;

    /**
     * Represents the Class of the bean
     */
    private final Class<?> beanClass;

    /**
     * The data file content as {@link Object}.
     */
    private final Object data;

    /**
     * Whether is a Java record or not.
     */
    private final boolean isRecord;

    public RoqDataBeanBuildItem(String name, Class<?> beanClass, Object data, boolean isRecord) {
        this.name = name;
        this.beanClass = beanClass;
        this.data = data;
        this.isRecord = isRecord;
    }

    public String getName() {
        return name;
    }

    public Class<?> getBeanClass() {
        return beanClass;
    }

    public Object getData() {
        return this.data;
    }

    public boolean isRecord() {
        return isRecord;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        RoqDataBeanBuildItem that = (RoqDataBeanBuildItem) object;
        return isRecord == that.isRecord && Objects.equals(name, that.name) && Objects.equals(beanClass, that.beanClass)
                && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, beanClass, data, isRecord);
    }
}
