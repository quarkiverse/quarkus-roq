package io.quarkiverse.roq.frontmatter.deployment.exception;

public class RoqLayoutNotFoundException extends RuntimeException {

    public RoqLayoutNotFoundException(String message) {
        super(message);
    }

    public RoqLayoutNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
