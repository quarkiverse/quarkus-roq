package io.quarkiverse.roq.frontmatter.runtime.exception;

import io.quarkiverse.roq.exception.RoqException;

public class RoqStaticFileException extends RoqException {

    public RoqStaticFileException(RoqException.Builder builder) {
        super(builder);
    }
}
