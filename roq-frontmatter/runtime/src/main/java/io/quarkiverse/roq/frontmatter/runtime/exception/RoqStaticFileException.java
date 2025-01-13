package io.quarkiverse.roq.frontmatter.runtime.exception;

public class RoqStaticFileException extends RuntimeException {

    public RoqStaticFileException(String message) {
        super(message);
    }

    public RoqStaticFileException(String message, Throwable cause) {
        super(message, cause);
    }

}
