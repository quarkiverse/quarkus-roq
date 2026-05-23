package io.quarkiverse.roq.frontmatter.runtime.exception;

import io.quarkiverse.roq.exception.RoqException;

public class RoqTemplateLinkException extends RoqException {

    public RoqTemplateLinkException(RoqException.Builder builder) {
        super(builder);
    }
}
