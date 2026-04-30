package io.quarkiverse.roq.exception;

public record RoqSourceInfo(String relativePath, String absolutePath, Integer line, Integer column) {

    public RoqSourceInfo(String relativePath) {
        this(relativePath, null, null, null);
    }

    public RoqSourceInfo(String relativePath, String absolutePath) {
        this(relativePath, absolutePath, null, null);
    }
}
