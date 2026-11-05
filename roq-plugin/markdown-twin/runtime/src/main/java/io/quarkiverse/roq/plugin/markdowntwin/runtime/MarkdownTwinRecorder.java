package io.quarkiverse.roq.plugin.markdowntwin.runtime;

import io.quarkiverse.roq.LargeRecords;
import io.quarkus.runtime.annotations.Recorder;

/**
 * Carries the set of generated twins from the build to {@link MarkdownTwins}.
 */
@Recorder
public class MarkdownTwinRecorder {

    /**
     * @param resourcePath the serialized map of page resource path to twin resource path, see
     *        {@code LargeRecordBuildItem}
     */
    public void init(String resourcePath) {
        MarkdownTwins.init(LargeRecords.deserialize(resourcePath));
    }
}
