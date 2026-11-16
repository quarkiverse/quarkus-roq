package io.quarkiverse.roq.plugin.image.runtime;

import io.quarkiverse.roq.exception.RoqException;

/**
 * An image error reported with Roq's structured details: at build time an expression that cannot be evaluated,
 * a preset that does not exist or an image the site does not have, at render time a source that was not
 * converted while {@code quarkus.qute.image.strict} is on.
 */
public class RoqImageException extends RoqException {

    public RoqImageException(RoqException.Builder builder) {
        super(builder);
    }
}
