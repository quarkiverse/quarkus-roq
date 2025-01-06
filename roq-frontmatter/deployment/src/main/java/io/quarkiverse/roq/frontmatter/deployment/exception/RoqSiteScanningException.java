package io.quarkiverse.roq.frontmatter.deployment.exception;

import java.io.IOException;
import java.io.UncheckedIOException;

public class RoqSiteScanningException extends UncheckedIOException {
    public RoqSiteScanningException(String message, IOException cause) {
        super(message, cause);
    }
}
