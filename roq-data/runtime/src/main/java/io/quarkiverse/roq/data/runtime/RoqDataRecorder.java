package io.quarkiverse.roq.data.runtime;

import io.quarkiverse.roq.LargeRecords;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqDataRecorder {

    public RuntimeValue<Object> createRoqDataJson(String resourcePath) {
        return new RuntimeValue<>(LargeRecords.deserializeJson(resourcePath));
    }

    public RuntimeValue<Object> createRoqDataBean(Object data) {
        return new RuntimeValue<>(data);
    }
}
