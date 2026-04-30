package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataConflictException extends RoqException {

    public DataConflictException(RoqException.Builder builder) {
        super(builder);
    }
}
