package io.quarkiverse.roq.frontmatter.deployment.exception;

public class RoqSiteScanningException extends RuntimeException {
    public RoqSiteScanningException(String message, Throwable cause) {
        super(message, cause);
    }

    public RoqSiteScanningException(String message) {
        super(message);
    }
}
