package io.quarkiverse.roq.generator.runtime;

import java.util.Set;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqGeneratorRecorder {

    public void setStaticPaths(Set<String> staticPaths) {
        FixedStaticPagesProvider.setStaticPaths(staticPaths);
    }

    public void setOutputTarget(String outputDirectory) {
        FixedStaticPagesProvider.setOutputTarget(outputDirectory);
    }
}
