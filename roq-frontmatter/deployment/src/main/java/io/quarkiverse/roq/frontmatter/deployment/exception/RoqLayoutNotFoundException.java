package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public class RoqLayoutNotFoundException extends RoqException {

    public RoqLayoutNotFoundException(RoqException.Builder builder) {
        super(builder);
    }
}
