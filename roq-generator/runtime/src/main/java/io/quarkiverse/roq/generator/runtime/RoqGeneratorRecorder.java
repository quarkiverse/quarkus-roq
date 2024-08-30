package io.quarkiverse.roq.generator.runtime;

import java.util.List;
import java.util.Set;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqGeneratorRecorder {

    public void setStaticPaths(Set<String> staticPaths) {
        ConfiguredPathsProvider.setStaticPaths(staticPaths);
    }

    public void setOutputTarget(String outputDirectory) {
        ConfiguredPathsProvider.setOutputTarget(outputDirectory);
    }

    public void setSelectedPaths(List<String> selectedPaths) {
        ConfiguredPathsProvider.setSelectedPaths(selectedPaths);
    }
}
