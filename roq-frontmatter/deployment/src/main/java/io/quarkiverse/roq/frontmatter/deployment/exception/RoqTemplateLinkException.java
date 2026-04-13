package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public class RoqTemplateLinkException extends RoqException {

    public RoqTemplateLinkException(RoqException.Builder builder) {
        super(builder);
    }
}
