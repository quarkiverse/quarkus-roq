package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

/**
 * @deprecated Use {@link io.quarkiverse.roq.frontmatter.runtime.exception.RoqTemplateLinkException} instead.
 */
@Deprecated(forRemoval = true)
public class RoqTemplateLinkException extends io.quarkiverse.roq.frontmatter.runtime.exception.RoqTemplateLinkException {

    public RoqTemplateLinkException(RoqException.Builder builder) {
        super(builder);
    }
}
