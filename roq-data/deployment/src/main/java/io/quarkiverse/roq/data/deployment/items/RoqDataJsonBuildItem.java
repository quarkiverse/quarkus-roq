package io.quarkiverse.roq.data.deployment.items;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A build item representing a Roq data file.
 */
public final class RoqDataJsonBuildItem extends MultiBuildItem {

    /**
     * The name of the Roq data file.
     */
    private final String name;

    /**
     * The content of the Roq data file as a JSON string.
     */
    private final Object data;

    public RoqDataJsonBuildItem(String name, Object data) {
        this.name = name;
        this.data = data;
    }

    public String getName() {
        return name;
    }

    public Object getData() {
        return data;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object)
            return true;
        if (object == null || getClass() != object.getClass())
            return false;
        RoqDataJsonBuildItem that = (RoqDataJsonBuildItem) object;
        return Objects.equals(name, that.name) && Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, data);
    }
}
