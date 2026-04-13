package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public class RoqPathConflictException extends RoqException {

    public RoqPathConflictException(RoqException.Builder builder) {
        super(builder);
    }
}
