package io.quarkiverse.roq.deployment.items;

import java.util.Set;

import io.quarkus.builder.item.MultiBuildItem;

/**
 * A resource containing serialized build-time data, intended for payloads too large for the
 * bytecode recorder's constant pool (65,535 entries). The data is stored as a generated classpath
 * resource and deserialized at runtime via {@link io.quarkiverse.roq.LargeRecords}.
 * <p>
 * A dedicated processor converts these items into the corresponding
 * {@code GeneratedResourceBuildItem}, {@code NativeImageResourceBuildItem}, and
 * {@code ReflectiveClassBuildItem} (with serialization support) automatically.
 */
public final class LargeRecordBuildItem extends MultiBuildItem {

    private final String resourcePath;
    private final byte[] data;
    private final Set<Class<?>> serializableClasses;

    /**
     * @param resourcePath classpath resource path (e.g. {@code "roq-recorded/sources.ser"})
     * @param data serialized bytes, typically produced by {@link io.quarkiverse.roq.LargeRecords#serialize}
     * @param serializableClasses classes that must be registered for native-image serialization
     */
    public LargeRecordBuildItem(String resourcePath, byte[] data, Class<?>... serializableClasses) {
        this.resourcePath = resourcePath;
        this.data = data;
        this.serializableClasses = Set.of(serializableClasses);
    }

    public String resourcePath() {
        return resourcePath;
    }

    public byte[] data() {
        return data;
    }

    public Set<Class<?>> serializableClasses() {
        return serializableClasses;
    }
}
