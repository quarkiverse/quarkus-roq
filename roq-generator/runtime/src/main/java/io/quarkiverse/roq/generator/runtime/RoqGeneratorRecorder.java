package io.quarkiverse.roq.generator.runtime;

import java.util.Map;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqGeneratorRecorder {

    public void setStaticFiles(Map<String, StaticFile> staticFiles) {
        ConfiguredPathsProvider.setStaticFiles(staticFiles);
    }

    public void setOutputTarget(String outputDirectory) {
        ConfiguredPathsProvider.setOutputTarget(outputDirectory);
    }

    public void setBuildSelectedPaths(RoqSelection selectedPaths) {
        ConfiguredPathsProvider.setBuildSelectedPaths(selectedPaths);
    }
}
