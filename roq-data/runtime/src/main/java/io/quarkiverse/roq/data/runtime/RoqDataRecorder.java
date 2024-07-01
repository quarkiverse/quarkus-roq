package io.quarkiverse.roq.data.runtime;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqDataRecorder {

    public RuntimeValue<Object> createRoqDataJson(Object data) {
        return new RuntimeValue<>(data);
    }
}
