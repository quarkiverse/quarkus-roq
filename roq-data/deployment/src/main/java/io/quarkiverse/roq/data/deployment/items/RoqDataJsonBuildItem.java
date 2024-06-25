package io.quarkiverse.roq.data.deployment.items;

import java.util.Objects;

import io.quarkus.builder.item.MultiBuildItem;
import io.vertx.core.json.JsonObject;

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
    private final JsonObject jsonObject;

    public RoqDataJsonBuildItem(String name, JsonObject jsonObject) {
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
        RoqDataJsonBuildItem that = (RoqDataJsonBuildItem) o;
        return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
