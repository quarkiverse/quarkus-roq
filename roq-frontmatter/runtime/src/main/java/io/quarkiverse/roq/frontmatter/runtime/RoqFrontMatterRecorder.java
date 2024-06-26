package io.quarkiverse.roq.frontmatter.runtime;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.json.JsonObject;

@Recorder
public class RoqFrontMatterRecorder {

    public RuntimeValue<JsonObject> createRoqDataJson(JsonObject jsonObject) {
        return new RuntimeValue<>(jsonObject);
    }
}
