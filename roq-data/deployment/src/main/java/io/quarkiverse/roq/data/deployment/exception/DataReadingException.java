package io.quarkiverse.roq.data.deployment.exception;

import java.io.IOException;
import java.io.UncheckedIOException;

public class DataReadingException extends UncheckedIOException {
    public DataReadingException(String message, IOException cause) {
        super(message, cause);
    }
}
