package io.quarkiverse.roq.plugin.qrcode.runtime;

import io.quarkus.arc.Arc;
import io.quarkus.arc.impl.LazyValue;
import io.quarkus.qute.EngineConfiguration;

/**
 * Qute template section helper for generating QR codes.
 * Extends QuteBarCode to provide QR code generation functionality using the Okapi library.
 */
@EngineConfiguration
public class RoqQrCode extends QuteBarCode {

    private static final LazyValue<QRCodeRenderer> CONVERTER = new LazyValue<>(
            () -> Arc.container().instance(QRCodeRenderer.class).get());

    /**
     * Constructs a new RoqQrCode instance.
     * Registers the "qrcode" section helper with the encode method.
     */
    public RoqQrCode() {
        super("qrcode", RoqQrCode::encode);
    }

    /**
     * Encodes a QR code as an SVG image and returns it wrapped in an HTML img tag.
     *
     * @param value The content to encode in the QR code
     * @param alt The alt text for the generated image
     * @param foreground The foreground color (hex format with optional # prefix)
     * @param background The background color (hex format with optional # prefix)
     * @param width The width of the generated image in pixels
     * @param height The height of the generated image in pixels
     * @return An HTML img tag containing the base64-encoded SVG QR code
     */
    private static String encode(String value, String alt, String foreground, String background, int width, int height,
            Boolean asciidoc) {
        return CONVERTER.get().encode(value, alt, foreground, background, width, height, asciidoc);
    }

}
