package io.quarkiverse.roq.data.runtime;

import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;

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

    /**
     * The writer is created on first use: an application which doesn't use it doesn't pay for the Jackson mapper.
     */
    public Supplier<DataMappingWriter> dataWriterSupplier(Map<String, String> sourceFiles, String propertyNamingStrategy) {
        return () -> new DataMappingWriter(sourceFiles, new DataSerializer(namingStrategy(propertyNamingStrategy)));
    }

    private static PropertyNamingStrategy namingStrategy(String propertyNamingStrategy) {
        PropertyNamingStrategy strategy = null;
        if (propertyNamingStrategy != null) {
            try {
                strategy = (PropertyNamingStrategy) Class
                        .forName(propertyNamingStrategy, true, Thread.currentThread().getContextClassLoader())
                        .getDeclaredConstructor().newInstance();
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to instantiate property naming strategy " + propertyNamingStrategy,
                        e);
            }
        }
        return strategy;
    }
}
