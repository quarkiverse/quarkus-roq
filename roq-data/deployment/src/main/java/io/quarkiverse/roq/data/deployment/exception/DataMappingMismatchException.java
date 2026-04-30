package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataMappingMismatchException extends RoqException {

    public DataMappingMismatchException(RoqException.Builder builder) {
        super(builder);
    }
}
