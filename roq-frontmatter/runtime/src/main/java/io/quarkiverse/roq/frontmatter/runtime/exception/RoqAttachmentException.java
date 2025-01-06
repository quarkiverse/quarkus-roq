package io.quarkiverse.roq.frontmatter.runtime.exception;

public class RoqAttachmentException extends RuntimeException {

    public RoqAttachmentException(String message) {
        super(message);
    }

    public RoqAttachmentException(String message, Throwable cause) {
        super(message, cause);
    }

}
