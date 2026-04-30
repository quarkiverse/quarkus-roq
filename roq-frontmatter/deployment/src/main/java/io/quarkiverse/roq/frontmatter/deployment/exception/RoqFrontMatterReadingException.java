package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class RoqFrontMatterReadingException extends RoqException {

    public RoqFrontMatterReadingException(RoqException.Builder builder) {
        super(builder);
    }
}
