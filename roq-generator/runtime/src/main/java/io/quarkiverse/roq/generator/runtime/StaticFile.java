package io.quarkiverse.roq.generator.runtime;

import java.io.Serializable;

public record StaticFile(String path, FetchType type) implements Serializable {
    public enum FetchType {
        FILE,
        CLASSPATH,
        HTTP
    }
}
