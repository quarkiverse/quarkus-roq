package io.quarkiverse.roq.plugin.image.runtime;

import java.nio.file.Path;

import jakarta.inject.Singleton;

import io.quarkiverse.qute.web.image.runtime.RuntimeImageListener;
import io.quarkiverse.roq.generator.runtime.ConfiguredPathsProvider;
import io.quarkiverse.roq.generator.runtime.SelectedPath;
import io.quarkiverse.roq.generator.runtime.StaticFile;

/**
 * Hooks dynamically-generated images into Roq's static site generation.
 * When a dynamic image is converted at render time, this registers it
 * as a static file (for direct copy) and enqueues its path for the generator
 * to pick up in the next drain cycle.
 */
@Singleton
public class RoqRuntimeImageListener implements RuntimeImageListener {

    @Override
    public void onImageConverted(String outputPath, Path convertedFile) {
        ConfiguredPathsProvider.addStaticFile(outputPath,
                new StaticFile(convertedFile.toAbsolutePath().toString(), StaticFile.FetchType.FILE));
        ConfiguredPathsProvider.enqueuePath(
                SelectedPath.builder().path(outputPath).build());
    }
}
