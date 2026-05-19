package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataBindingException extends RoqException {

    public DataBindingException(RoqException.Builder builder) {
        super(builder);
    }
}
