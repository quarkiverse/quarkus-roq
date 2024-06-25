package io.quarkiverse.roq.runtime;

import java.util.Set;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class RoqRecorder {

    public void setStaticPaths(Set<String> staticPaths) {
        FixedStaticPagesProvider.setStaticPaths(staticPaths);
    }

    public void setOutputTarget(String outputDirectory) {
        FixedStaticPagesProvider.setOutputTarget(outputDirectory);
    }
}
