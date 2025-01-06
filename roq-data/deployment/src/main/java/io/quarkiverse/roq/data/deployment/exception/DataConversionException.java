package io.quarkiverse.roq.data.deployment.exception;

import java.io.IOException;
import java.io.UncheckedIOException;

public class DataConversionException extends UncheckedIOException {

    public DataConversionException(String message, IOException cause) {
        super(message, cause);
    }
}
