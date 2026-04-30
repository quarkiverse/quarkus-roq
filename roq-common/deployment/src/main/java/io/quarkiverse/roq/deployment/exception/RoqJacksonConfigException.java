package io.quarkiverse.roq.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class RoqJacksonConfigException extends RoqException {
    public RoqJacksonConfigException(RoqException.Builder builder) {
        super(builder);
    }
}
