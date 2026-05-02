package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataMappingRequiredFileException extends RoqException {

    public DataMappingRequiredFileException(RoqException.Builder builder) {
        super(builder);
    }
}
