package io.quarkiverse.roq.generator.runtime;

public enum PageSource {
    /**
     * This page is configured through {@link RoqGeneratorConfig#paths()}
     */
    CONFIG,

    /**
     * This page has been provided at runtime through a
     */
    PROVIDED
}
