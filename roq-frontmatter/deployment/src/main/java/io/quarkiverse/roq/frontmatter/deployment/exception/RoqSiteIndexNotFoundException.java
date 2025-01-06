package io.quarkiverse.roq.frontmatter.deployment.exception;

public class RoqSiteIndexNotFoundException extends RuntimeException {

    public RoqSiteIndexNotFoundException(String message) {
        super(message);
    }

    public RoqSiteIndexNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

}
