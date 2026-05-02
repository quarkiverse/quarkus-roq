package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataConversionException extends RoqException {

    public DataConversionException(RoqException.Builder builder) {
        super(builder);
    }
}
