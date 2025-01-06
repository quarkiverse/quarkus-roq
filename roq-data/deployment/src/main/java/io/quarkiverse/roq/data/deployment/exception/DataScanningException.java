package io.quarkiverse.roq.data.deployment.exception;

import java.io.IOException;
import java.io.UncheckedIOException;

public class DataScanningException extends UncheckedIOException {
    public DataScanningException(String message, IOException cause) {
        super(message, cause);
    }
}
