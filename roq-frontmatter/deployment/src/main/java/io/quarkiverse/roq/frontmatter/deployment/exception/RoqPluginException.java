package io.quarkiverse.roq.frontmatter.deployment.exception;

public class RoqPluginException extends RuntimeException {

    public RoqPluginException(String message) {
        super(message);
    }

    public RoqPluginException(String message, Throwable cause) {
        super(message, cause);
    }

}
