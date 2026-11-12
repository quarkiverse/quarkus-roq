package io.quarkiverse.roq.data.runtime;

import java.util.Map;
import java.util.function.Supplier;

import io.quarkiverse.roq.LargeRecords;
import io.quarkiverse.roq.RoqJacksonSettings;
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

    /**
     * The writer is created on first use: an application which doesn't use it doesn't pay for the Jackson mapper.
     */
    public Supplier<DataMappingWriter> dataWriterSupplier(Map<String, String> sourceFiles, RoqJacksonSettings settings) {
        return () -> new DataMappingWriter(sourceFiles, new DataSerializer(settings));
    }
}
