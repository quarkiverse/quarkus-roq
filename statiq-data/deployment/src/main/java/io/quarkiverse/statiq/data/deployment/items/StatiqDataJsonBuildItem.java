package io.quarkiverse.statiq.data.deployment.items;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

/**
 * A build item representing a Statiq data file.
 */
public final class StatiqDataJsonBuildItem extends MultiBuildItem {

    /**
     * The name of the Statiq data file.
     */
    private final String name;

    /**
     * The content of the Statiq data file as a JSON string.
     */
    private final JsonObject jsonObject;

    public StatiqDataJsonBuildItem(String name, JsonObject jsonObject) {
        this.name = name;
        this.jsonObject = jsonObject;
    }

    public String getName() {
        return name;
    }

    public JsonObject getJsonObject() {
        return jsonObject;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        StatiqDataJsonBuildItem that = (StatiqDataJsonBuildItem) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
