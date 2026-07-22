package io.quarkiverse.roq.data.runtime;

import io.quarkiverse.roq.EncodedJson;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqDataRecorder {

    public RuntimeValue<Object> createRoqDataJson(EncodedJson data) {
        return new RuntimeValue<>(data.get());
    }

    public RuntimeValue<Object> createRoqDataBean(Object data) {
        return new RuntimeValue<>(data);
    }
}
