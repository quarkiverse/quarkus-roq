package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public class RoqSiteScanningException extends RoqException {

    public RoqSiteScanningException(RoqException.Builder builder) {
        super(builder);
    }
}
