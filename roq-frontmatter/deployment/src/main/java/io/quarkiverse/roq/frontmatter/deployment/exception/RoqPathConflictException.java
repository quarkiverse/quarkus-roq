package io.quarkiverse.roq.frontmatter.deployment.exception;

public class RoqPathConflictException extends RuntimeException {

    public RoqPathConflictException(String message) {
        super(message);
    }

    public RoqPathConflictException(String message, Throwable cause) {
        super(message, cause);
    }

}
