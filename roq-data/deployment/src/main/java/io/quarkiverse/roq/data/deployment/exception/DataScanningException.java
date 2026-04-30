package io.quarkiverse.roq.data.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class DataScanningException extends RoqException {

    public DataScanningException(RoqException.Builder builder) {
        super(builder);
    }
}
