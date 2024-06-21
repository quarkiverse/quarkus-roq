package io.quarkiverse.statiq.data.runtime;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.json.JsonObject;

@Recorder
public class StatiqDataRecorder {

    public RuntimeValue<JsonObject> createStatiqDataJson(JsonObject jsonObject) {
        return new RuntimeValue<>(jsonObject);
    }
}
