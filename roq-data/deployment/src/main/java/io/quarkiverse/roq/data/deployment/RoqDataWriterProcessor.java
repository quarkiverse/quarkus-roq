package io.quarkiverse.roq.data.deployment;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import jakarta.inject.Singleton;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import io.quarkiverse.roq.data.deployment.items.DataMappingBuildItem;
import io.quarkiverse.roq.data.deployment.items.RoqDataBuildItem;
import io.quarkiverse.roq.data.runtime.DataMappingWriter;
import io.quarkiverse.roq.data.runtime.RoqDataRecorder;
import io.quarkiverse.roq.data.runtime.annotations.DataMapping;
import io.quarkiverse.roq.deployment.items.RoqJacksonBuildItem;
import io.quarkiverse.tools.projectscanner.ProjectFile;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * Provides the {@link DataMappingWriter} bean (reverse mode), which writes {@link DataMapping} beans back to their source
 * file.
 */
class RoqDataWriterProcessor {

    @BuildStep
    @Record(ExecutionTime.STATIC_INIT)
    void dataWriter(List<DataMappingBuildItem> mappings,
            List<RoqDataBuildItem> dataFiles,
            RoqJacksonBuildItem jackson,
            BuildProducer<SyntheticBeanBuildItem> beans,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClasses,
            RoqDataRecorder recorder) {
        // only local project files can be written: classpath resources are in the build output
        final Map<String, Path> localFiles = dataFiles.stream()
                .filter(file -> file.origin() == ProjectFile.Origin.LOCAL_PROJECT_FILE)
                .collect(Collectors.toMap(RoqDataBuildItem::getName, RoqDataBuildItem::sourceFile));
        final Map<String, String> sourceFiles = new TreeMap<>();
        for (DataMappingBuildItem mapping : mappings) {
            final String className;
            if (!mapping.isParentType()) {
                className = mapping.getClassName().toString();
            } else if (mapping.getMappingType() == DataMapping.Type.ARRAY_FILE) {
                className = mapping.getParentType().toString();
            } else {
                // directory mappings are not writable
                continue;
            }
            final Path source = localFiles.get(mapping.getName());
            if (source != null) {
                sourceFiles.put(className, source.toAbsolutePath().normalize().toString());
            }
        }

        final PropertyNamingStrategy strategy = jackson.getJsonMapper().getSerializationConfig()
                .getPropertyNamingStrategy();
        final String strategyClass = strategy == null ? null : strategy.getClass().getName();
        if (strategyClass != null) {
            reflectiveClasses.produce(ReflectiveClassBuildItem.builder(strategyClass).constructors().build());
        }

        // Lazy and removable: no cost for applications which don't inject it
        beans.produce(SyntheticBeanBuildItem.configure(DataMappingWriter.class)
                .scope(Singleton.class)
                .supplier(recorder.dataWriterSupplier(sourceFiles, strategyClass))
                .done());
    }
}
