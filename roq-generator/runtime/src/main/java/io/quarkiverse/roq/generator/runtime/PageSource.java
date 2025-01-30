package io.quarkiverse.roq.generator.runtime;

public enum PageSource {
    /**
     * This page is configured through {@link RoqGeneratorConfig}
     */
    CONFIG,

    /**
     * This page is configured through a build item
     */
    BUILD_ITEM,

    /**
     * This page has been @Provided at runtime through a RoqSelection
     */
    PROVIDED
}
