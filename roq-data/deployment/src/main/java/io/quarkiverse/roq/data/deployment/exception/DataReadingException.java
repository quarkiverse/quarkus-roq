package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataReadingException extends RoqException {

    public DataReadingException(RoqException.Builder builder) {
        super(builder);
    }
}
