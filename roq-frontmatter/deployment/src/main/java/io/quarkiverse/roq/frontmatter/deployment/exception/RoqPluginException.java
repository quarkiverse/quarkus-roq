package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public class RoqPluginException extends RoqException {

    public RoqPluginException(RoqException.Builder builder) {
        super(builder);
    }
}
