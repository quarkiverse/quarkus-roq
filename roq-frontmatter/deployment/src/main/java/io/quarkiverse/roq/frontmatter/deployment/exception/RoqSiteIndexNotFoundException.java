package io.quarkiverse.roq.frontmatter.deployment.exception;

import io.quarkiverse.roq.exception.RoqException;

public class RoqSiteIndexNotFoundException extends RoqException {

    public RoqSiteIndexNotFoundException(RoqException.Builder builder) {
        super(builder);
    }
}
