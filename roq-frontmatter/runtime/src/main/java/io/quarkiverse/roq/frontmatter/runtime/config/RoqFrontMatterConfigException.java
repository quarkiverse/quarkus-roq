package io.quarkiverse.roq.frontmatter.runtime.config;

import io.quarkiverse.roq.frontmatter.runtime.exception.RoqException;

public class RoqFrontMatterConfigException extends RoqException {

    public RoqFrontMatterConfigException(RoqException.Builder builder) {
        super(builder);
    }
}
