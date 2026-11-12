package io.quarkiverse.roq.data.runtime;

import java.util.Locale;
import java.util.Optional;

/**
 * Supported Roq data file formats.
 */
public enum DataFormat {
    YAML,
    JSON;

    /**
     * Resolve the format from a file name extension ({@code .yaml}, {@code .yml} or {@code .json}).
     */
    public static Optional<DataFormat> fromFileName(String fileName) {
        final int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        return switch (fileName.substring(dot + 1).toLowerCase(Locale.ROOT)) {
            case "yaml", "yml" -> Optional.of(YAML);
            case "json" -> Optional.of(JSON);
            default -> Optional.empty();
        };
    }
}
