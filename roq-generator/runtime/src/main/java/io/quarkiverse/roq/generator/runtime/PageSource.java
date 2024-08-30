package io.quarkiverse.roq.generator.runtime;

public enum PageSource {
    /**
     * This page is configured through {@link RoqGeneratorConfig#paths()}
     */
    CONFIG,

    /**
     * This page is configured through a build item
     */
    BUILD_ITEM,

    /**
     * This page has been provided at runtime through a
     */
    PROVIDED
}
