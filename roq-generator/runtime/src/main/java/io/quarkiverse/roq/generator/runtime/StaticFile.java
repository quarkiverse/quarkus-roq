package io.quarkiverse.roq.generator.runtime;

public record StaticFile(String path, FetchType type) {
    public enum FetchType {
        FILE,
        CLASSPATH,
        HTTP
    }
}
