package io.quarkiverse.statiq.runtime;

import java.util.Set;

import io.quarkus.runtime.annotations.Recorder;

@Recorder
public class StatiqRecorder {

    public void setStatiqPages(Set<String> staticPaths) {
        FixedStaticPagesProvider.setStaticPaths(staticPaths);
    }

    public void setOutputTarget(String outputDirectory) {
        FixedStaticPagesProvider.setOutputTarget(outputDirectory);
    }
}
