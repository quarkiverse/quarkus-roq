package io.quarkiverse.statiq.data.deployment;

import java.util.function.Function;

import io.vertx.core.json.JsonObject;

public class JsonConverter implements Function<String, JsonObject> {
    @Override
    public JsonObject apply(String content) {
        return new JsonObject(content);
    }
}
