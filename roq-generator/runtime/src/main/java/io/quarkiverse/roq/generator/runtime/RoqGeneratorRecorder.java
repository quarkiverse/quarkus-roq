package io.quarkiverse.roq.generator.runtime;

import java.util.Map;

import io.quarkiverse.roq.LargeRecords;
import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqGeneratorRecorder {

    /**
     * @param resourcePath the serialized static files, see {@code LargeRecordBuildItem}
     */
    public void setStaticFiles(String resourcePath) {
        Map<String, StaticFile> staticFiles = LargeRecords.deserialize(resourcePath);
        ConfiguredPathsProvider.setStaticFiles(staticFiles);
    }

    public void setOutputTarget(String outputDirectory) {
        ConfiguredPathsProvider.setOutputTarget(outputDirectory);
    }

    /**
     * @param resourcePath the serialized selection, see {@code LargeRecordBuildItem}
     */
    public void setBuildSelectedPaths(String resourcePath) {
        RoqSelection selectedPaths = LargeRecords.deserialize(resourcePath);
        ConfiguredPathsProvider.setBuildSelectedPaths(selectedPaths);
    }
}
