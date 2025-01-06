package io.quarkiverse.roq.frontmatter.deployment.exception;

public class RoqFrontMatterReadingException extends RuntimeException {

    public RoqFrontMatterReadingException(String message) {
        super(message);
    }

    public RoqFrontMatterReadingException(String message, Throwable cause) {
        super(message, cause);
    }

}
